package com.muzik.desktop.player

import com.muzik.desktop.player.ffmpeg.AudioPlayer
import com.muzik.desktop.player.ffmpeg.HttpRangeInputStream
import com.muzik.desktop.player.ffmpeg.SabrInputStream
import com.muzik.desktop.youtube.Extraction
import com.muzik.desktop.youtube.Extraction.ResolvedStream
import com.muzik.desktop.youtube.InnerTube
import com.muzik.desktop.youtube.Song
import com.metrolist.innertubex.sabr.SabrAudioStream
import com.metrolist.innertubex.sabr.SabrBootstrap
import com.metrolist.innertubex.sabr.SabrChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerState(
    val queue: List<Song> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val autoplayRadio: Boolean = true,
) {
    val current: Song? get() = queue.getOrNull(index)
}

/**
 * Queue + playback engine. Audio is decoded and rendered through the FFmpeg
 * desktop stack ported from Metrolist (AudioPlayer → AudioDecoder →
 * AudioRenderer), with streams opened in Metrolist's priority order:
 * SABR transport → bounded-range HTTP input → direct URL. All FFmpeg work
 * happens on the engine's worker threads; Compose only ever reads [state].
 */
class PlayerEngine(
    private val highQualityProvider: suspend () -> Boolean = { false },
    private val onSongStarted: suspend (Song) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val generation = java.util.concurrent.atomic.AtomicLong(0)

    private var audio: AudioPlayer =
        AudioPlayer(
            onPlaybackCompleted = { _ -> scope.launch { advance(auto = true, forward = true) } },
            onPlaybackFailed = { _, throwable ->
                if (generation.get() > 0) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isPlaying = false,
                            error = "Playback failed: ${throwable.message?.lineSequence()?.firstOrNull() ?: throwable.javaClass.simpleName}",
                        )
                    }
                }
            },
        )

    // ---------------------------------------------------------------- control

    fun playFrom(queue: List<Song>, index: Int) {
        require(queue.isNotEmpty())
        _state.update { it.copy(queue = queue, index = index.coerceIn(0, queue.size - 1), error = null) }
        loadCurrent()
    }

    fun playPause() {
        val s = _state.value
        val player = audio
        when {
            player.isPrepared && player.isPlaying() -> {
                player.pause()
                _state.update { it.copy(isPlaying = false) }
            }
            player.isPrepared -> {
                player.resume()
                _state.update { it.copy(isPlaying = true, error = null) }
            }
            s.current != null -> loadCurrent()
        }
    }

    fun next(auto: Boolean = false) = advance(auto, forward = true)

    fun previous() {
        val s = _state.value
        // A playing track restarts on Previous; otherwise jump back one slot.
        if (s.positionMs > 3000) {
            seekTo(0)
            return
        }
        advance(auto = false, forward = false)
    }

    fun seekTo(ms: Long) {
        _state.update { it.copy(positionMs = ms) }
        audio.seekTo(ms.coerceAtLeast(0))
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        audio.setVolume(clamped)
    }

    fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) }
    }

    fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(shuffle = enabled) }
    }

    fun setAutoplayRadio(enabled: Boolean) {
        _state.update { it.copy(autoplayRadio = enabled) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun dispose() {
        scope.cancel()
        audio.close()
    }

    // ----------------------------------------------------------------- engine

    private fun advance(auto: Boolean, forward: Boolean) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (!forward) {
            val prev = if (s.index > 0) s.index - 1 else s.queue.size - 1
            _state.update { it.copy(index = prev, error = null) }
            loadCurrent()
            return
        }
        if (auto && s.repeat == RepeatMode.ONE) {
            audio.seekTo(0)
            audio.resume()
            _state.update { it.copy(positionMs = 0, isPlaying = true) }
            return
        }
        if (s.shuffle && s.queue.size > 1) {
            var candidate = s.index
            while (candidate == s.index) candidate = Random.nextInt(s.queue.size)
            _state.update { it.copy(index = candidate, error = null) }
            loadCurrent()
            return
        }
        if (s.index + 1 < s.queue.size) {
            _state.update { it.copy(index = s.index + 1, error = null) }
            loadCurrent()
            return
        }
        when {
            s.repeat == RepeatMode.ALL -> {
                _state.update { it.copy(index = 0, error = null) }
                loadCurrent()
            }
            s.autoplayRadio && s.current != null -> {
                scope.launch {
                    val extended = extendRadio()
                    if (extended && generation.get() > 0) {
                        _state.update { it.copy(index = it.index + 1, error = null) }
                        loadCurrent()
                    } else {
                        audio.pause()
                        _state.update { it.copy(isPlaying = false) }
                    }
                }
            }
            else -> {
                if (auto) {
                    audio.pause()
                    _state.update { it.copy(isPlaying = false) }
                }
            }
        }
    }

    private fun loadCurrent() {
        val s = _state.value
        val song = s.current ?: return
        val gen = generation.incrementAndGet()

        _state.update { it.copy(isLoading = true, positionMs = 0, durationMs = song.durationSeconds * 1000L) }

        scope.launch {
            try {
                val high = try { highQualityProvider() } catch (_: Exception) { false }
                val stream = Extraction.resolve(song.videoId, high)
                if (generation.get() != gen) return@launch
                mount(stream, gen)
            } catch (e: Exception) {
                if (generation.get() != gen) return@launch
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = "Couldn't play \"${song.title}\".\n${e.message?.lineSequence()?.firstOrNull() ?: e.javaClass.simpleName}",
                    )
                }
            }
        }

        scope.launch { onSongStarted(song) }
    }

    /** Opens the resolved stream through the Metrolist priority chain and starts playback. */
    private fun mount(stream: ResolvedStream, gen: Long) {
        _state.update { it.copy(isLoading = true) }
        audio.setNormalizationGain(Extraction.normalizationGain(stream.loudnessDb))
        val prepared =
            try {
                prepare(stream)
            } catch (e: Exception) {
                false
            }
        if (generation.get() != gen) return
        if (!prepared) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = "Couldn't open the audio stream (profile ${stream.profileId}).",
                )
            }
            return
        }
        audio.play()
        _state.update { it.copy(isLoading = false, isPlaying = true) }
        pollPosition()
    }

    private fun prepare(stream: ResolvedStream): Boolean {
        // 1. SABR transport (protobuf-chunked) — used when YouTube issues a SABR bootstrap.
        stream.sabrBootstrap?.let { bootstrap ->
            val input =
                SabrInputStream(
                    mediaSize = bootstrap.contentLengthBytes ?: stream.contentLengthBytes,
                    expectedDurationMs = bootstrap.durationMs,
                    streamFactory = { initialPlayerTimeMs, playbackPositionMs ->
                        sabrAudioChunks(bootstrap, initialPlayerTimeMs, playbackPositionMs)
                    },
                )
            val max = (bootstrap.contentLengthBytes ?: stream.contentLengthBytes ?: 1L)
                .coerceIn(1L, Int.MAX_VALUE.toLong() - 8L).toInt()
            if (audio.prepareStream(input, max, startPaused = false)) return true
            runCatching { input.close() }
        }
        // 2. Bounded-range HTTP input — only when the content length is known.
        val size = stream.contentLengthBytes
        if (!stream.requireBoundedRange || size != null) {
            if (size != null && size > 0L) {
                val input =
                    HttpRangeInputStream(
                        url = stream.url,
                        requestHeaders = stream.headers,
                        mediaSize = size,
                        chunkSize =
                            stream.rangeChunkSizeBytes.takeIf { it > 0L }
                                ?.coerceIn(1L, Int.MAX_VALUE.toLong())?.toInt()
                                ?: DEFAULT_HTTP_RANGE_CHUNK_BYTES,
                    )
                val max = size.coerceIn(1L, Int.MAX_VALUE.toLong() - 8L).toInt()
                if (audio.prepareStream(input, max, startPaused = false)) return true
                runCatching { input.close() }
            }
        }
        // 3. Direct URL — FFmpeg opens it with the extraction headers itself.
        return audio.prepareFile(stream.url, stream.headers, startPaused = false)
    }

    /** Tokenless SABR chunk flow, mirroring Metrolist's playback wrapper. */
    private fun sabrAudioChunks(
        bootstrap: SabrBootstrap,
        initialPlayerTimeMs: Long,
        playbackPositionMs: () -> Long,
    ): Flow<SabrChunk> =
        SabrAudioStream(
            httpClient = InnerTube.http,
            bootstrap = bootstrap,
            playbackPositionMs = playbackPositionMs,
            initialPlayerTimeMs = initialPlayerTimeMs,
        ).chunks()

    /** Single position sampler: the AudioPlayer drives its own clock. */
    private fun pollPosition() {
        val gen = generation.get()
        scope.launch {
            while (generation.get() == gen) {
                val dur = audio.getDuration()
                val pos = audio.getCurrentPosition()
                _state.update {
                    it.copy(
                        positionMs = pos,
                        durationMs = if (dur > 0) dur else it.durationMs,
                    )
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }

    /** Extends the queue with the YouTube Music radio when the last track ends. */
    suspend fun extendRadio(): Boolean {
        val s = _state.value
        val seed = s.current ?: return false
        if (s.queue.any { it.videoId != seed.videoId } && s.queue.size >= 20) return false
        return try {
            val more = InnerTube.radio(seed.videoId).filterNot { q -> s.queue.any { it.videoId == q.videoId } }
            if (more.isEmpty()) return false
            _state.update { it.copy(queue = it.queue + more.take(25)) }
            true
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val DEFAULT_HTTP_RANGE_CHUNK_BYTES = 1_048_576
    }
}
