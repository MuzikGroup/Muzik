@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.muzik.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import com.muzik.desktop.library.AudioQuality
import com.muzik.desktop.library.Library
import com.muzik.desktop.library.Playlist
import com.muzik.desktop.library.ThemeMode
import com.muzik.desktop.player.PlayerEngine
import com.muzik.desktop.updater.UpdateInfo
import com.muzik.desktop.updater.Updater
import com.muzik.desktop.util.BuildConfigVersion
import com.muzik.desktop.youtube.AlbumDetail
import com.muzik.desktop.youtube.Artist
import com.muzik.desktop.youtube.InnerTube
import com.muzik.desktop.youtube.SearchFilter
import com.muzik.desktop.youtube.SearchPage
import com.muzik.desktop.youtube.Song
import kotlinx.coroutines.launch

val LocalLibrary = staticCompositionLocalOf<Library> { error("Library not provided") }

sealed class Screen {
    data object Search : Screen()
    data object Library : Screen()
    data class AlbumPage(val browseId: String) : Screen()
    data class ArtistPage(val browseId: String) : Screen()
    data class PlaylistPage(val playlistId: String) : Screen()
}

/** Mutable UI state holder for the search screen; owns its own loading coroutines. */
class SearchState(private val ioScope: kotlinx.coroutines.CoroutineScope) {
    var query by mutableStateOf("")
    var filter by mutableStateOf(SearchFilter.SONGS)
    var suggestions by mutableStateOf<List<String>>(emptyList())
    var page by mutableStateOf<SearchPage?>(null)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun loadSuggestions(input: String) {
        ioScope.launch {
            suggestions = try { InnerTube.suggestions(input) } catch (_: Exception) { emptyList() }
        }
    }

    fun run(query: String, filter: SearchFilter) {
        ioScope.launch {
            loading = true
            loadingMore = false
            error = null
            page = try {
                InnerTube.search(query, filter)
            } catch (e: Exception) {
                error = e.message ?: "Search failed"
                null
            }
            loading = false
        }
    }

    fun loadMore() {
        val current = page ?: return
        val cont = current.continuation ?: return
        if (loadingMore) return
        ioScope.launch {
            loadingMore = true
            try {
                val more = InnerTube.search(query, filter, continuation = cont)
                page = SearchPage(
                    songs = current.songs + more.songs,
                    albums = current.albums + more.albums,
                    artists = current.artists + more.artists,
                    continuation = more.continuation,
                )
            } catch (_: Exception) {
                // Keep the current page; the user can scroll again to retry.
            }
            loadingMore = false
        }
    }
}

@Composable
fun App(library: Library, player: PlayerEngine) {
    CompositionLocalProvider(LocalLibrary provides library) {
        val settings by library.settings.collectAsState()
        val backStack = remember { mutableStateListOf<Screen>(Screen.Search) }
        val screen = backStack.last()
        var showQueue by remember { mutableStateOf(false) }
        var showSettings by remember { mutableStateOf(false) }
        var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
        var albumDetail by remember { mutableStateOf<AlbumDetail?>(null) }
        var artist by remember { mutableStateOf<Artist?>(null) }
        var currentPlaylist by remember { mutableStateOf<Playlist?>(null) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()

        val searchState = remember { SearchState(scope) }

        fun push(s: Screen) {
            backStack.add(s)
            showQueue = false
        }

        // Update check on launch.
        LaunchedEffect(Unit) {
            if (settings.checkUpdates) {
                val info = Updater.latestVersion() ?: return@LaunchedEffect
                if (Updater.isNewer(info.version, BuildConfigVersion.VERSION)) updateInfo = info
            }
        }

        // Live search suggestions with a short debounce.
        LaunchedEffect(searchState.query) {
            val q = searchState.query
            if (q.length >= 2) {
                kotlinx.coroutines.delay(300)
                if (searchState.query == q) searchState.loadSuggestions(q)
            }
        }

        // Load content for detail screens.
        LaunchedEffect(screen) {
            when (val s = screen) {
                is Screen.AlbumPage -> {
                    albumDetail = null
                    albumDetail = try { InnerTube.album(s.browseId) } catch (_: Exception) { null }
                }
                is Screen.ArtistPage -> {
                    artist = null
                    artist = try { InnerTube.artist(s.browseId) } catch (_: Exception) { null }
                }
                is Screen.PlaylistPage -> currentPlaylist = library.playlists.value.firstOrNull { it.id == s.playlistId }
                else -> Unit
            }
        }

        fun playQueue(songs: List<Song>, index: Int) {
            player.playFrom(songs, index)
        }

        fun queueNext(song: Song) {
            val s = player.state.value
            val insertAt = (s.index + 1).coerceAtMost(s.queue.size)
            val newQueue = s.queue.toMutableList().apply { add(insertAt, song) }
            player.playFrom(newQueue, if (s.index >= 0) s.index else 0)
        }

        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                NavigationRailItem(
                    selected = screen is Screen.Search,
                    onClick = { push(Screen.Search) },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    label = { Text("Search") },
                )
                NavigationRailItem(
                    selected = screen is Screen.Library || screen is Screen.PlaylistPage,
                    onClick = { push(Screen.Library) },
                    icon = { Icon(Icons.Filled.LibraryMusic, contentDescription = "Library") },
                    label = { Text("Library") },
                )
                NavigationRailItem(
                    selected = false,
                    onClick = { showSettings = true },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                )
            }

            Column(Modifier.weight(1f)) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (backStack.size > 1) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Text(
                        when (screen) {
                            Screen.Search -> "Search"
                            Screen.Library -> "Your Library"
                            is Screen.AlbumPage -> albumDetail?.album?.title ?: "Album"
                            is Screen.ArtistPage -> artist?.name ?: "Artist"
                            is Screen.PlaylistPage -> currentPlaylist?.name ?: "Playlist"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Box(Modifier.weight(1f)) {
                    when (screen) {
                        Screen.Search -> SearchScreen(
                            library = library,
                            onPlayQueue = ::playQueue,
                            onQueueSong = ::queueNext,
                            onOpenAlbum = { push(Screen.AlbumPage(it)) },
                            onOpenArtist = { push(Screen.ArtistPage(it)) },
                            searchState = searchState,
                            onQueryChange = { searchState.query = it },
                            onFilterChange = { searchState.filter = it },
                            onSubmit = { q -> searchState.run(q, searchState.filter) },
                        )
                        Screen.Library -> LibraryScreen(
                            library = library,
                            onPlayQueue = ::playQueue,
                            onOpenPlaylist = { push(Screen.PlaylistPage(it)) },
                            onCreatePlaylist = { library.createPlaylist(it) },
                            onRenamePlaylist = { id, name -> library.renamePlaylist(id, name) },
                            onDeletePlaylist = { library.deletePlaylist(it) },
                        )
                        is Screen.AlbumPage -> AlbumScreen(
                            detail = albumDetail,
                            onBack = { backStack.removeLastOrNull() },
                            onOpenArtist = { push(Screen.ArtistPage(it)) },
                            onPlayQueue = ::playQueue,
                        )
                        is Screen.ArtistPage -> ArtistScreen(
                            artist = artist,
                            onOpenAlbum = { push(Screen.AlbumPage(it)) },
                            onPlayQueue = ::playQueue,
                        )
                        is Screen.PlaylistPage -> PlaylistScreen(
                            playlist = currentPlaylist,
                            library = library,
                            onBack = { backStack.removeLastOrNull() },
                            onPlayQueue = ::playQueue,
                            onRemoveSong = { videoId -> currentPlaylist?.id?.let { library.removeFromPlaylist(it, videoId); currentPlaylist = library.playlists.value.firstOrNull { p -> p.id == it } } },
                        )
                    }

                    if (showQueue) {
                        QueueSheet(player = player, onClose = { showQueue = false })
                    }
                }

                PlayerBar(player = player, library = library, onToggleQueue = { showQueue = !showQueue })
            }
        }

        if (showSettings) {
            SettingsDialog(library = library, onDismiss = { showSettings = false }, player = player)
        }

        updateInfo?.let { info ->
            ModalBottomSheet(onDismissRequest = { updateInfo = null }) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Muzik ${info.version} is available", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "A new version of Muzik Desktop is ready. Update for the latest fixes and features.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val url = info.assetUrl ?: info.notesUrl
                            try { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) } catch (_: Exception) { }
                        }) { Text("Download update") }
                        OutlinedButton(onClick = { updateInfo = null }) { Text("Later") }
                    }
                }
            }
        }
    }
}
