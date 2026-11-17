package com.muzik.desktop.player.ffmpeg

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/** Seekable media source contract shared by range/SABR inputs (Metrolist desktop). */
interface SeekableMediaSource {
    val mediaSize: Long?
    val mediaPosition: Long
    val bufferedBytePosition: Long get() = mediaPosition

    fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun readAtMediaPosition(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int

    fun seekToMediaPosition(position: Long): Long

    fun updatePlaybackPosition(positionMs: Long)

    fun close()
}

internal interface SeekableMediaInput : SeekableMediaSource

/** Lets the player push the current playback position into the media input. */
internal interface PlaybackPositionInput {
    val streamFailure: Throwable?

    fun updatePlaybackPosition(positionMs: Long)

    fun beginPlaybackSeek(positionMs: Long) = updatePlaybackPosition(positionMs)

    fun endPlaybackSeek(positionMs: Long) = updatePlaybackPosition(positionMs)
}

internal enum class AudioPlayerFailureSource {
    DECODER,
    RENDERER,
    STREAM,
}

/** Carries an HTTP status code on playback failures so callers can classify them. */
interface PlaybackHttpStatusCarrier {
    val playbackHttpStatusCode: Int?
}

fun Throwable.findPlaybackHttpStatusCode(): Int? =
    generateSequence(this) { current -> current.cause?.takeUnless { it === current } }
        .mapNotNull { current -> (current as? PlaybackHttpStatusCarrier)?.playbackHttpStatusCode }
        .firstOrNull()

fun normalizationBoostPcm16(
    sample: Short,
    gain: Float,
): Short {
    if (gain <= 1f) return sample
    val boosted = sample / 32768.0 * gain
    val magnitude = abs(boosted)
    val limited = if (magnitude <= 0.9) boosted else sign(boosted) * (0.9 + 0.1 * tanh((magnitude - 0.9) / 0.1))
    return (limited * 32768.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
}

class InstantSilenceDetector(
    private val sampleRate: Int,
) {
    private var silentFrames = 0L
    val isSilent: Boolean get() = silentFrames >= sampleRate * 2L

    fun frame(peak: Int) {
        silentFrames = if (peak < 256) silentFrames + 1 else 0
    }

    fun reset() {
        silentFrames = 0
    }
}

fun instantSilenceSeekTarget(
    positionMs: Long,
    durationMs: Long,
): Long? {
    if (durationMs <= 15_000L) return null
    return minOf(positionMs + 15_000L, durationMs - 500L).takeIf { it > positionMs }
}

