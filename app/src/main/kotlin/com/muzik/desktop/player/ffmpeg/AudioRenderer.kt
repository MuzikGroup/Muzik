package com.muzik.desktop.player.ffmpeg

import com.muzik.desktop.player.ffmpeg.normalizationBoostPcm16
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

/**
 * PCM renderer over a Java Sound [SourceDataLine]. Ported from Metrolist's
 * desktop player with the equalizer and tempo processor removed; everything
 * that matters for reliable output — generation-guarded writes, pause/resume,
 * flush/drain, hardware+software volume and the playback position clock — is
 * kept as-is.
 */
class AudioRenderer private constructor(
    private val sampleRate: Int,
    private val channelCount: Int,
    lineFactory: (AudioFormat, Int) -> SourceDataLine,
) {
    constructor(sampleRate: Int, channelCount: Int) :
        this(sampleRate, channelCount, ::openAudioLine)

    internal constructor(
        sampleRate: Int,
        channelCount: Int,
        line: SourceDataLine,
    ) : this(sampleRate, channelCount, { _, _ -> line })

    private val audioFormat = AudioFormat(sampleRate.toFloat(), 16, channelCount, true, false)
    private val line: SourceDataLine
    private val masterGainControl: FloatControl?

    @Volatile
    private var hardwareVolumeApplied = false

    @Volatile
    private var volume = 1.0f

    @Volatile
    var normalizationGain = 1f

    @Volatile
    private var isPlaying = false

    @Volatile
    private var hasWrittenAudio = false

    private var writtenFrameCount = 0L
    private var expectingOutput = false
    private var lastWriteAtNs = 0L
    private var lastUnderrunAtNs = 0L
    private var underrunCount = 0L

    @Volatile
    private var outputGeneration = 0L
    private val positionClock = AudioPositionClock(audioFormat.sampleRate)
    private val silenceSkipper = PcmSilenceSkipper(sampleRate, channelCount)

    fun isLongSilence(): Boolean = synchronized(safeLock) { silenceSkipper.isLongSilence }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val safeLock = Object()

    init {
        val latency = 0.15
        val bufferSize = (latency * audioFormat.sampleRate * audioFormat.frameSize).toInt()
        line =
            lineFactory(audioFormat, bufferSize).apply {
                if (!isOpen) open(audioFormat, bufferSize)
                start()
                isPlaying = true
            }

        masterGainControl =
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                line.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
            } else {
                null
            }
    }

    fun writePcmData(
        data: ByteArray?,
        isCurrent: () -> Boolean = { true },
    ) {
        if (data == null || data.isEmpty()) return
        val (output, generation) =
            synchronized(safeLock) {
                if (!line.isOpen || !isCurrent()) return
                val input = silenceSkipper.process(data, false, false) { _, _ -> }
                val chunks = if (input.isNotEmpty()) listOf(input) else emptyList()
                chunks to outputGeneration
            }
        if (output.isEmpty()) return
        recordOutputUnderrun(generation)
        for (chunk in output) {
            if (!writeOutput(chunk, generation, isCurrent)) return
        }
    }

    private fun writeOutput(
        data: ByteArray,
        generation: Long,
        isCurrent: () -> Boolean = { true },
    ): Boolean {
        val currentVol = if (volume == 0f || !hardwareVolumeApplied) volume else 1f
        val boost = normalizationGain
        val processedData =
            if (currentVol != 1f || boost > 1f) {
                applyVolume(data, currentVol, boost)
            } else {
                data
            }
        var offset = 0
        while (offset < processedData.size) {
            val writtenBytes =
                synchronized(safeLock) {
                    while (generation == outputGeneration && line.isOpen && !isPlaying) {
                        try {
                            safeLock.wait()
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return false
                        }
                    }
                    if (generation != outputGeneration || !line.isOpen || !isCurrent()) return false
                    val writableBytes =
                        minOf(processedData.size - offset, line.available()) / audioFormat.frameSize * audioFormat.frameSize
                    if (writableBytes == 0) {
                        val bufferedFrames = (line.bufferSize - line.available()).coerceAtLeast(0) / audioFormat.frameSize
                        val waitMs = (bufferedFrames * 500L / sampleRate).coerceIn(1L, 20L)
                        try {
                            safeLock.wait(waitMs)
                        } catch (_: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return false
                        }
                        0
                    } else {
                        line.write(processedData, offset, writableBytes).also { writtenBytes ->
                            if (writtenBytes > 0) {
                                writtenFrameCount += writtenBytes / audioFormat.frameSize
                                hasWrittenAudio = true
                                expectingOutput = true
                                lastWriteAtNs = System.nanoTime()
                            }
                        }
                    }
                }
            if (writtenBytes > 0) {
                offset += writtenBytes
            }
        }
        return true
    }

    private fun recordOutputUnderrun(generation: Long) {
        synchronized(safeLock) {
            if (generation != outputGeneration || !line.isOpen || !isPlaying || !expectingOutput) return
            val bufferBytes = line.bufferSize
            if (bufferBytes <= 0 || line.available() < bufferBytes) return
            expectingOutput = false
            underrunCount++
            val now = System.nanoTime()
            if (lastUnderrunAtNs != 0L && now - lastUnderrunAtNs < 1_000_000_000L) return
            lastUnderrunAtNs = now
            println(
                "AudioRenderer: output buffer emptied between writes " +
                    "(underrunCount=$underrunCount audioBufferMs=${bufferBytes * 1_000L / audioFormat.frameSize / sampleRate})",
            )
        }
    }

    private var volumeBuffer = ByteArray(0)

    private fun applyVolume(
        data: ByteArray,
        volume: Float,
        boost: Float,
    ): ByteArray {
        if (volumeBuffer.size != data.size) {
            volumeBuffer = ByteArray(data.size)
        }
        val result = volumeBuffer
        var byteIdx = 0
        val len = data.size - 1
        while (byteIdx < len) {
            val low = data[byteIdx].toInt() and 0xFF
            val high = data[byteIdx + 1].toInt()
            val sample = (high shl 8) or low
            val scaled =
                (
                    normalizationBoostPcm16(
                        sample.toShort(),
                        boost,
                    ) * volume
                ).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            result[byteIdx] = (scaled and 0xFF).toByte()
            result[byteIdx + 1] = ((scaled shr 8) and 0xFF).toByte()
            byteIdx += 2
        }
        return result
    }

    fun resetPosition(mediaMs: Long) {
        synchronized(safeLock) {
            outputGeneration++
            expectingOutput = false
            safeLock.notifyAll()
            val lineFrame = line.longFramePosition
            writtenFrameCount = lineFrame
            positionClock.reset(mediaMs, lineFrame, 1f)
            silenceSkipper.reset()
            hasWrittenAudio = false
        }
    }

    fun getPlayedPositionMs(): Long =
        if (line.isOpen) {
            positionClock.position(line.longFramePosition)
        } else {
            0L
        }

    fun pause() {
        synchronized(safeLock) {
            isPlaying = false
            expectingOutput = false
            safeLock.notifyAll()
            if (line.isOpen && line.isActive) {
                try {
                    line.stop()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun resume() {
        synchronized(safeLock) {
            isPlaying = true
            if (line.isOpen && !line.isActive) {
                try {
                    line.start()
                } catch (_: Exception) {
                }
            }
            safeLock.notifyAll()
        }
    }

    fun flush() {
        synchronized(safeLock) {
            outputGeneration++
            expectingOutput = false
            safeLock.notifyAll()
            silenceSkipper.reset()
            if (line.isOpen) {
                line.flush()
            }
        }
    }

    fun drain(shouldContinue: () -> Boolean): Boolean {
        val (pending, generation) =
            synchronized(safeLock) {
                if (!shouldContinue()) return false
                expectingOutput = false
                val tail = silenceSkipper.drain()
                val chunks = if (tail.isNotEmpty()) listOf(tail) else emptyList()
                chunks to outputGeneration
            }
        for (chunk in pending) {
            if (!shouldContinue() || !writeOutput(chunk, generation)) return false
        }
        val drainTimeoutMs =
            synchronized(safeLock) {
                if (generation != outputGeneration || !shouldContinue()) return false
                expectingOutput = false
                val bufferedBytes = (line.bufferSize.toLong() - line.available()).coerceAtLeast(0L)
                val bytesPerSecond = sampleRate.toLong() * audioFormat.frameSize
                (bufferedBytes * 1_000L + bytesPerSecond - 1L) / bytesPerSecond + 250L
            }
        // SourceDataLine.drain() cannot be interrupted reliably on every mixer.
        // Some Linux mixers never report an empty buffer, so allow the queued duration plus scheduling margin.
        return awaitAudioDrain(
            timeoutMs = drainTimeoutMs,
            shouldContinue = { generation == outputGeneration && shouldContinue() },
            awaitResume = {
                synchronized(safeLock) {
                    while (generation == outputGeneration && line.isOpen && !isPlaying && shouldContinue()) safeLock.wait()
                }
            },
            hasPendingAudio = {
                synchronized(safeLock) { line.isOpen && line.available() < line.bufferSize }
            },
        )
    }

    fun setVolume(volume: Float) {
        val clampedVol = volume.coerceIn(0.0f, 1.0f)
        this.volume = clampedVol
        hardwareVolumeApplied = false
        masterGainControl?.let { control ->
            try {
                if (clampedVol <= 0.0001f) {
                    control.value = control.minimum
                } else {
                    val dB = (20.0f * log10(clampedVol)).coerceIn(control.minimum, control.maximum)
                    control.value = dB
                }
                hardwareVolumeApplied = true
            } catch (_: Exception) {
                runCatching { control.value = 0f.coerceIn(control.minimum, control.maximum) }
            }
        }
    }

    fun getVolume(): Float = this.volume

    fun isPlaying(): Boolean = isPlaying && hasWrittenAudio

    fun setPlaybackSpeed(speed: Float) {
        // Tempo processing is not exposed in Muzik's UI; accept for API parity.
    }

    fun setPlaybackPitch(pitch: Float) {
        // Pitch processing is not exposed in Muzik's UI; accept for API parity.
    }

    fun setPreferredDevice(device: Any?) {
    }

    fun getAudioSessionId(): Int = 0

    fun close() {
        synchronized(safeLock) {
            outputGeneration++
            safeLock.notifyAll()
            isPlaying = false
            hasWrittenAudio = false
            if (line.isOpen) {
                line.flush()
                line.stop()
                line.close()
            }
        }
    }
}

/** Tracks played media milliseconds from written line frames, immune to mixer clock drift. */
internal class AudioPositionClock(
    private val sampleRate: Float,
) {
    private data class Segment(
        val lineFrame: Long,
        val mediaMs: Long,
        val speed: Float,
    )

    private val segments = mutableListOf(Segment(0L, 0L, 1f))
    private var lastPositionMs = 0L
    private var anchorMs = 0L
    private var skippedFrames = 0L
    private val silenceOffsets = mutableListOf<Pair<Long, Long>>()

    @Synchronized
    fun skipSilence(
        outputFrames: Long,
        frames: Long,
    ) {
        skippedFrames += frames
        val boundary = anchorMs + (outputFrames * 1_000.0 / sampleRate).toLong()
        if (silenceOffsets.lastOrNull()?.first == boundary) silenceOffsets.removeAt(silenceOffsets.lastIndex)
        silenceOffsets += boundary to skippedFrames
    }

    @Synchronized
    fun finishSilence(
        lineFrame: Long,
        outputFrames: Long,
    ) {
        if (skippedFrames == 0L) return
        segments += Segment(lineFrame, anchorMs + (outputFrames * 1_000.0 / sampleRate).toLong(), segments.last().speed)
    }

    @Synchronized
    fun reset(
        mediaMs: Long,
        lineFrame: Long,
        playbackSpeed: Float,
    ) {
        lastPositionMs = mediaMs.coerceAtLeast(0L)
        anchorMs = lastPositionMs
        skippedFrames = 0
        silenceOffsets.clear()
        segments.clear()
        segments += Segment(lineFrame, lastPositionMs, playbackSpeed)
    }

    @Synchronized
    fun changeSpeed(
        firstFrameAtNewSpeed: Long,
        playbackSpeed: Float,
    ) {
        val previous = segments.last()
        segments +=
            Segment(
                lineFrame = firstFrameAtNewSpeed,
                mediaMs = positionIn(previous, firstFrameAtNewSpeed),
                speed = playbackSpeed,
            )
    }

    @Synchronized
    fun position(lineFrame: Long): Long {
        if (sampleRate <= 0f) return lastPositionMs
        val segmentIndex = segments.indexOfLast { lineFrame >= it.lineFrame }.coerceAtLeast(0)
        val position = positionIn(segments[segmentIndex], lineFrame)
        if (segmentIndex > 0) segments.subList(0, segmentIndex).clear()
        val silenceIndex = silenceOffsets.indexOfLast { position >= it.first }
        val removedMs = if (silenceIndex >= 0) silenceOffsets[silenceIndex].second * 1_000.0 / sampleRate else 0.0
        if (silenceIndex > 0) silenceOffsets.subList(0, silenceIndex).clear()
        lastPositionMs = (position + removedMs).toLong().coerceAtLeast(lastPositionMs)
        return lastPositionMs
    }

    private fun positionIn(
        segment: Segment,
        lineFrame: Long,
    ): Long =
        segment.mediaMs +
            (((lineFrame - segment.lineFrame).coerceAtLeast(0L) * 1_000.0 * segment.speed) / sampleRate).toLong()
}
