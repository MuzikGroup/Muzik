package com.muzik.desktop.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muzik.desktop.state.app.AppState
import com.muzik.desktop.state.content.ArtistState
import com.muzik.desktop.state.content.PodcastState
import com.muzik.desktop.state.playback.PlayerState
import com.muzik.desktop.storage.DownloadManager
import com.muzik.desktop.ui.components.PodcastContextMenu
import com.muzik.shared.model.toSongItem
import com.muzik.shared.ui.menu.contextMenuAnchor
import com.muzik.shared.ui.menu.rememberCursorContextMenuState
import com.muzik.shared.ui.navigation.LocalNavigator
import com.muzik.shared.ui.screens.PodcastContent
import com.muzik.shared.utils.ShareUrls

@Composable
fun PodcastScreen(colorScheme: ColorScheme) {
    val navigator = LocalNavigator.current
    val page = PodcastState.page
    val podcastMenu = rememberCursorContextMenuState()
    val songs = page?.episodes?.map { it.toSongItem(page.podcast.title) }.orEmpty()

    Box(Modifier.fillMaxSize().contextMenuAnchor(podcastMenu)) {
        PodcastContent(
            page = page,
            isLoading = PodcastState.isLoading,
            isSaving = false,
            error = PodcastState.error,
            onBackClick = { navigator.goBack() },
            detailsInDialog = true,
            onChannelClick = { ArtistState.fetchArtistData(it) },
            onRetry = PodcastState::retry,
            onLoadMore = PodcastState::loadMore,
            onPlay = PodcastState::playAll,
            onToggleSaved = PodcastState::togglePodcastSaved,
            onPodcastMenuClick = podcastMenu::open,
            onEpisodeClick = { episode ->
                val index = page?.episodes?.indexOfFirst { it.id == episode.id } ?: -1
                songs.getOrNull(index)?.let { PlayerState.playTrack(it, songs) }
            },
            onToggleEpisodeSaved = PodcastState::toggleEpisodeSaved,
            onPlayNext = { PlayerState.playNext(it.toSongItem(page?.podcast?.title)) },
            onAddToQueue = { PlayerState.addToQueue(it.toSongItem(page?.podcast?.title)) },
            onDownload = { DownloadManager.download(it.toSongItem(page?.podcast?.title)) },
            onShare = { AppState.copyToClipboard(ShareUrls.song(it.id)) },
            contentPadding = PaddingValues(bottom = 100.dp),
            currentEpisodeId = PlayerState.currentTrack?.id,
            isPlaying = PlayerState.isPlaying,
        )
        if (page != null) {
            PodcastContextMenu(
                podcast = page.podcast,
                songs = songs.takeIf { page.continuation == null }.orEmpty(),
                expanded = podcastMenu.expanded,
                offset = podcastMenu.offset,
                onDismiss = podcastMenu::dismiss,
                isSaved = page.saved,
                onToggleSaved = PodcastState::togglePodcastSaved,
            )
        }
    }
}
