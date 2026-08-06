package com.muzikgroup.innertube.pages

import com.muzikgroup.innertube.models.Album
import com.muzikgroup.innertube.models.AlbumItem
import com.muzikgroup.innertube.models.Artist
import com.muzikgroup.innertube.models.ArtistItem
import com.muzikgroup.innertube.models.MusicResponsiveListItemRenderer
import com.muzikgroup.innertube.models.MusicTwoRowItemRenderer
import com.muzikgroup.innertube.models.PlaylistItem
import com.muzikgroup.innertube.models.SongItem
import com.muzikgroup.innertube.models.YTItem
import com.muzikgroup.innertube.models.oddElements
import com.muzikgroup.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
