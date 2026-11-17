package com.muzik.desktop.player

import com.muzik.desktop.youtube.InnerTube
import com.muzik.desktop.youtube.Song
import com.muzik.desktop.youtube.StreamInfo
import com.muzik.desktop.youtube.StreamResolver
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicReference
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
 * Queue + playback engine. Audio decoding uses the JavaFX media stack
 * (native AAC/M4A decoder bundled per platform); queue state is exposed to
 * Compose through [state].
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

    private var fxPlayer: MediaPlayer? = null
    private val generation = java.util.concurrent.atomic.AtomicLong(0)

    companion object {
        @Volatile
        private var fxStarted = false

        /** Boots the JavaFX runtime exactly once, off the UI thread. */
        fun ensureFx() {
            if (!fxStarted) {
                synchronized(this) {
                    if (!fxStarted) {
                        Platform.setImplicitExit(false)
                        Platform.startup { }
                        fxStarted = true
                    }
                }
            }
        }
    }

    init {
        ensureFx()
        scope.launch {
            while (isActive) {
                delay(500)
                val player = fxPlayer ?: continue
                val pos = try {
                    (player.currentTime.toMillis().toLong())
                } catch (_: Exception) {
                    continue
                }
                _state.update { it.copy(positionMs = pos) }
            }
        }
    }

    // ---------------------------------------------------------------- control

    fun playFrom(queue: List<Song>, index: Int) {
        require(queue.isNotEmpty())
        _state.update { it.copy(queue = queue, index = index.coerceIn(0, queue.size - 1), error = null) }
        loadCurrent()
    }

    fun playPause() {
        val player = fxPlayer
        val s = _state.value
        when {
            player == null && s.current != null -> loadCurrent()
            player == null -> return
            else -> Platform.runLater {
                if (player.status == MediaPlayer.Status.PLAYING) player.pause() else player.play()
            }
        }
    }

    fun next(auto: Boolean = false) = advance(auto, forward = true)

    fun previous() = advance(auto = false, forward = false)

    fun seekTo(ms: Long) {
        val player = fxPlayer ?: return
        Platform.runLater {
            try {
                player.seek(Duration.millis(ms.toDouble()))
            } catch (_: Exception) {
            }
        }
        _state.update { it.copy(positionMs = ms) }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        val player = fxPlayer
        Platform.runLater { player?.volume = clamped.toDouble() }
    }

    fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) }
        fxPlayer?.let { p -> Platform.runLater { p.cycleCount = if (mode == RepeatMode.ONE) Int.MAX_VALUE else 1 } }
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
        Platform.runLater {
            fxPlayer?.dispose()
            fxPlayer = null
        }
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
        if (auto && s.repeat == RepeatMode.ONE && fxPlayer != null) {
            seekTo(0)
            Platform.runLater { fxPlayer?.play() }
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
                        Platform.runLater { fxPlayer?.pause() }
                        _state.update { it.copy(isPlaying = false) }
                    }
                }
            }
            else -> {
                if (auto) {
                    Platform.runLater { fxPlayer?.pause() }
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
                val stream = StreamResolver.resolve(song.videoId, high)
                Platform.runLater {
                    if (generation.get() != gen) return@runLater
                    mount(song, stream)
                }
            } catch (e: Exception) {
                if (generation.get() != gen) return@launch
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

    private fun mount(song: Song, stream: StreamInfo) {
        try {
            fxPlayer?.dispose()
        } catch (_: Exception) {
        }
        val gen = generation.get()
        try {
            val media = Media(stream.url)
            val player = MediaPlayer(media)
            player.volume = _volume.value.toDouble()
            player.cycleCount = if (_state.value.repeat == RepeatMode.ONE) Int.MAX_VALUE else 1

            player.setOnReady {
                if (generation.get() != gen) return@setOnReady
                val d = (player.media.duration.toMillis().toLong()).takeIf { it > 0 }
                _state.update { it.copy(isLoading = false, durationMs = d ?: it.durationMs) }
            }
            player.setOnEndOfMedia {
                if (generation.get() != gen) return@setOnEndOfMedia
                advance(auto = true, forward = true)
            }
            player.setOnError {
                if (generation.get() != gen) return@setOnError
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPlaying = false,
                        error = "Playback error on profile ${stream.profileId}. Retrying with the next profile may help.",
                    )
                }
            }
            player.statusProperty().addListener { _, _, st ->
                if (generation.get() != gen) return@addListener
                _state.update { it.copy(isPlaying = st == MediaPlayer.Status.PLAYING) }
            }
            fxPlayer = player
            player.play()
            _state.update { it.copy(isLoading = false) }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = "Couldn't mount stream: ${e.message ?: e.javaClass.simpleName}",
                )
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
}
