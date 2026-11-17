package com.muzik.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzik.desktop.library.Library
import com.muzik.desktop.library.Playlist
import com.muzik.desktop.youtube.AlbumDetail
import com.muzik.desktop.youtube.Artist
import com.muzik.desktop.youtube.SearchFilter
import com.muzik.desktop.youtube.SearchPage
import com.muzik.desktop.youtube.Song

@Composable
fun SearchScreen(
    library: Library,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onQueueSong: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var showSuggestions by remember { mutableStateOf(false) }
    LaunchedEffect(searchState.query) {
        showSuggestions = searchState.query.length >= 2 && searchState.suggestions.isNotEmpty()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = searchState.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search songs, albums, artists…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                if (showSuggestions) {
                    Surface(
                        shadowElevation = 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 68.dp),
                    ) {
                        Column {
                            searchState.suggestions.take(8).forEach { s ->
                                Text(
                                    s,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onQueryChange(s)
                                            onSubmit(s)
                                            showSuggestions = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Button(onClick = { onSubmit(searchState.query); showSuggestions = false }) {
                Text("Search")
            }
        }

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchFilter.entries.forEach { f ->
                AssistChip(
                    onClick = { onFilterChange(f); onSubmit(searchState.query) },
                    label = { Text(f.label) },
                    border = null,
                    leadingIcon = if (searchState.filter == f) {
                        { Icon(Icons.Filled.PlayArrow, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        val err = searchState.error
        when {
            err != null -> ErrorState(err)
            searchState.page == null && searchState.loading -> LoadingList()
            searchState.page != null -> ResultsList(
                page = searchState.page!!,
                library = library,
                onPlayQueue = onPlayQueue,
                onQueueSong = onQueueSong,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
                onLoadMore = { searchState.loadMore() },
                isLoadingMore = searchState.loadingMore,
            )
        }
    }
}

@Composable
private fun ResultsList(
    page: SearchPage,
    library: Library,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onQueueSong: (Song) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, page.continuation) {
        if (nearEnd && page.continuation != null && !isLoadingMore) onLoadMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (page.songs.isNotEmpty()) {
            itemsIndexed(page.songs) { i, song ->
                SongRow(
                    song = song,
                    index = i,
                    library = library,
                    isCurrent = false,
                    onPlay = { onPlayQueue(page.songs, i) },
                    onOpenArtist = onOpenArtist,
                    onOpenAlbum = onOpenAlbum,
                    onQueueSong = { onQueueSong(song) },
                )
            }
        }
        items(page.albums) { album ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenAlbum(album.browseId) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverArt(album.thumbnail, Modifier.size(56.dp))
                Column {
                    Text(album.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        listOfNotNull(album.artist, album.year).joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(page.artists) { artist ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenArtist(artist.browseId) }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverArt(artist.thumbnail, Modifier.size(56.dp), rounded = 100)
                Column {
                    Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (page.songs.isEmpty() && page.albums.isEmpty() && page.artists.isEmpty()) {
            item {
                Text(
                    "No results.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        if (isLoadingMore) {
            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } }
        }
    }
}

@Composable
fun AlbumScreen(detail: AlbumDetail?, onBack: () -> Unit, onOpenArtist: (String) -> Unit, onPlayQueue: (List<Song>, Int) -> Unit) {
    if (detail == null) return LoadingList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverArt(detail.album.thumbnail, Modifier.size(140.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Album", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(detail.album.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(detail.album.artist, detail.album.year).joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                detail.album.artistBrowseId?.let { id ->
                    TextButton(onClick = { onOpenArtist(id) }) { Text("View artist") }
                }
                Button(onClick = { onPlayQueue(detail.tracks, 0) }, enabled = detail.tracks.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play album")
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            itemsIndexed(detail.tracks) { i, song ->
                SongRow(
                    song = song,
                    index = i,
                    library = librarySingleton(),
                    onPlay = { onPlayQueue(detail.tracks, i) },
                    onOpenArtist = onOpenArtist,
                )
            }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist?, onOpenAlbum: (String) -> Unit, onPlayQueue: (List<Song>, Int) -> Unit) {
    if (artist == null) return LoadingList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverArt(artist.thumbnail, Modifier.size(120.dp), rounded = 100)
            Column {
                Text("Artist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(artist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(onClick = { onPlayQueue(artist.songs, 0) }, enabled = artist.songs.isNotEmpty(), modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play top songs")
                }
            }
        }
        if (artist.songs.isNotEmpty()) {
            Text(
                "Songs",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.height(320.dp).padding(horizontal = 8.dp)) {
                itemsIndexed(artist.songs.take(10)) { i, song ->
                    SongRow(song = song, index = i, library = librarySingleton(), onPlay = { onPlayQueue(artist.songs, i) })
                }
            }
        }
        if (artist.albums.isNotEmpty()) {
            Text(
                "Albums",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(artist.albums) { album ->
                    AlbumCard(album, { onOpenAlbum(album.browseId) }, Modifier.width(170.dp))
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    library: Library,
    onPlayQueue: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
) {
    val favorites by library.favorites.collectAsState()
    val playlists by library.playlists.collectAsState()
    val history by library.history.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Favorites", "Playlists", "History").forEachIndexed { i, label ->
                AssistChip(
                    onClick = { tab = i },
                    label = { Text(label, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal) },
                    border = null,
                )
            }
            Spacer(Modifier.weight(1f))
            if (tab == 1) {
                IconButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New playlist")
                }
            }
        }

        if (creating) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onCreatePlaylist(newName); newName = ""; creating = false }) { Text("Create") }
                TextButton(onClick = { creating = false }) { Text("Cancel") }
            }
        }

        when (tab) {
            0 -> if (favorites.isEmpty()) EmptyHint("Songs you like will appear here.")
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                itemsIndexed(favorites) { i, song ->
                    SongRow(song, index = i, library = library, onPlay = { onPlayQueue(favorites, i) })
                }
            }
            1 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                items(playlists) { pl ->
                    var editing by remember { mutableStateOf(false) }
                    var editedName by remember { mutableStateOf(pl.name) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenPlaylist(pl.id) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                        Column(Modifier.weight(1f)) {
                            if (editing) {
                                OutlinedTextField(
                                    value = editedName,
                                    onValueChange = { editedName = it },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(pl.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text("${pl.songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = {
                            if (editing) { onRenamePlaylist(pl.id, editedName); editing = false } else editing = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            else -> if (history.isEmpty()) EmptyHint("Your listening history will appear here.")
            else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                itemsIndexed(history) { _, entry ->
                    SongRow(song = entry.song, library = library, onPlay = { onPlayQueue(listOf(entry.song), 0) })
                }
            }
        }
    }
}

@Composable
fun PlaylistScreen(playlist: Playlist?, library: Library, onBack: () -> Unit, onPlayQueue: (List<Song>, Int) -> Unit, onRemoveSong: (String) -> Unit) {
    if (playlist == null) return LoadingList()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Column(Modifier.weight(1f)) {
                Text("Playlist", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(playlist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Button(onClick = { onPlayQueue(playlist.songs, 0) }, enabled = playlist.songs.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            itemsIndexed(playlist.songs) { i, song ->
                SongRow(
                    song = song,
                    index = i,
                    library = library,
                    onPlay = { onPlayQueue(playlist.songs, i) },
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Library is a process-wide singleton created in Main; screens receive it via
 * composition local so deep rows can like songs without threading it everywhere.
 */
@Composable
private fun librarySingleton(): Library = LocalLibrary.current
