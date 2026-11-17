package com.muzik.desktop.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.muzik.desktop.youtube.Song
import java.io.File

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class HistoryEntry(
    val song: Song,
    val playedAt: Long = System.currentTimeMillis(),
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AudioQuality { LOW, NORMAL, HIGH }

@Serializable
data class Settings(
    val theme: String = "SYSTEM",
    val audioQuality: String = "NORMAL",
    val checkUpdates: Boolean = true,
    val volume: Float = 1.0f,
)

class Library {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val dir: File = appDataDir().resolve("Muzik").apply { mkdirs() }
    private val settingsFile = dir.resolve("settings.json")
    private val favoritesFile = dir.resolve("favorites.json")
    private val playlistsFile = dir.resolve("playlists.json")
    private val historyFile = dir.resolve("history.json")

    private val _settings = MutableStateFlow(load(settingsFile, Settings()))
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _favorites = MutableStateFlow(loadList<Song>(favoritesFile))
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _playlists = MutableStateFlow(loadList<Playlist>(playlistsFile))
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _history = MutableStateFlow(loadList<HistoryEntry>(historyFile))
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    // -------------------------------------------------------------- settings

    fun updateSettings(transform: (Settings) -> Settings) {
        _settings.value = transform(_settings.value)
        persist(settingsFile, _settings.value)
    }

    // -------------------------------------------------------------- favorites

    fun isFavorite(videoId: String): Boolean = _favorites.value.any { it.videoId == videoId }

    fun toggleFavorite(song: Song) {
        val current = _favorites.value
        _favorites.value =
            if (current.any { it.videoId == song.videoId }) current.filterNot { it.videoId == song.videoId }
            else listOf(song) + current
        persist(favoritesFile, _favorites.value)
    }

    // -------------------------------------------------------------- playlists

    fun createPlaylist(name: String): Playlist {
        val pl = Playlist(id = "PL${System.currentTimeMillis()}", name = name.ifBlank { "New playlist" })
        _playlists.value = _playlists.value + pl
        persist(playlistsFile, _playlists.value)
        return pl
    }

    fun renamePlaylist(id: String, name: String) {
        _playlists.value = _playlists.value.map { if (it.id == id) it.copy(name = name) else it }
        persist(playlistsFile, _playlists.value)
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filterNot { it.id == id }
        persist(playlistsFile, _playlists.value)
    }

    fun addToPlaylist(playlistId: String, song: Song) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId && pl.songs.none { it.videoId == song.videoId }) {
                pl.copy(songs = pl.songs + song)
            } else pl
        }
        persist(playlistsFile, _playlists.value)
    }

    fun removeFromPlaylist(playlistId: String, videoId: String) {
        _playlists.value = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(songs = pl.songs.filterNot { it.videoId == videoId }) else pl
        }
        persist(playlistsFile, _playlists.value)
    }

    // --------------------------------------------------------------- history

    fun recordPlay(song: Song) {
        _history.value = (listOf(HistoryEntry(song)) + _history.value.filterNot { it.song.videoId == song.videoId })
            .take(500)
        persist(historyFile, _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist(historyFile, _history.value)
    }

    // --------------------------------------------------------------- helpers

    private inline fun <reified T> load(file: File, default: T): T = try {
        if (file.exists()) json.decodeFromString<T>(file.readText()) else default
    } catch (_: Exception) {
        default
    }

    private inline fun <reified T> loadList(file: File): List<T> = try {
        if (file.exists()) json.decodeFromString<List<T>>(file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private inline fun <reified T> persist(file: File, value: T) {
        scope.launch {
            try {
                file.writeText(json.encodeToString(value))
            } catch (_: Exception) {
                // Persistence is best-effort; never crash the player on IO issues.
            }
        }
    }

    companion object {
        fun appDataDir(): File {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") ->
                    File(System.getenv("APPDATA") ?: System.getProperty("user.home") + "/AppData/Roaming")
                os.contains("mac") ->
                    File(System.getProperty("user.home") + "/Library/Application Support")
                else -> File(
                    System.getenv("XDG_DATA_HOME")
                        ?: System.getProperty("user.home") + "/.local/share"
                ).apply { mkdirs() }
            }
        }
    }
}
