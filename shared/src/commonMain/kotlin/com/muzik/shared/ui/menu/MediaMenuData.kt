package com.muzik.shared.ui.menu

import com.muzik.shared.api.playlistSongs
import com.muzik.shared.model.AlbumItem
import com.muzik.shared.model.ArtistItem
import com.muzik.shared.model.EpisodeItem
import com.muzik.shared.model.PlaylistItem
import com.muzik.shared.model.PodcastItem
import com.muzik.shared.model.SongItem
import com.muzik.shared.model.YTItem
import com.muzik.shared.model.toSongItem
import com.muzik.shared.state.GlobalYouTubeRepository

suspend fun loadMediaItemSongs(item: YTItem): List<SongItem> {
    val repository = GlobalYouTubeRepository.instance
    return when (item) {
        is SongItem -> {
            listOf(item)
        }

        is EpisodeItem -> {
            listOf(item.toSongItem())
        }

        is AlbumItem -> {
            repository.getAlbum(item.id)?.songs.orEmpty()
        }

        is ArtistItem -> {
            repository.getArtistSongs(item.id)
        }

        is PlaylistItem -> {
            repository.getPlaylist(item.id).playlistSongs()
        }

        is PodcastItem -> {
            // Every page, so playback covers the whole podcast and can start from its first episode.
            buildList {
                repository.getPodcastPages(item.id).collect { page -> page.episodes.mapTo(this) { it.toSongItem(page.podcast.title) } }
            }
        }

        else -> {
            emptyList()
        }
    }
}
