/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.ui.menu

import androidx.compose.runtime.Composable
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.details
import com.muzik.shared.generated.resources.details_desc
import com.muzik.shared.generated.resources.listen_together
import com.muzik.shared.generated.resources.refetch
import com.muzik.shared.generated.resources.refetch_desc
import com.muzik.shared.generated.resources.remove_from_queue
import com.muzik.shared.model.ArtistTiny
import com.muzik.shared.settings.Settings
import com.muzik.shared.ui.components.PlayerMenuAction
import com.muzik.shared.ui.icons.MuzikIcons
import org.jetbrains.compose.resources.stringResource

@Composable
fun queueMediaMenuModel(
    songTitle: String,
    artistName: String,
    artists: List<ArtistTiny>,
    albumName: String?,
    thumbnailUrl: String?,
    isDisliked: Boolean,
    onDismiss: () -> Unit,
    onStartRadio: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onDownload: (() -> Unit)?,
    onGoToAlbum: (() -> Unit)?,
    onGoToArtist: ((String) -> Unit)?,
    onShare: (() -> Unit)?,
    onAltShare: (() -> Unit)?,
    onRefetch: (() -> Unit)?,
    onShowInfo: (() -> Unit)?,
    onRemoveFromQueue: (() -> Unit)?,
    onNavigateToListenTogether: (() -> Unit)?,
    onDislike: (() -> Unit)?,
    deleteUploadAction: PlayerMenuAction? = null,
): MediaMenuModel =
    MediaMenuModel(
        title = songTitle,
        subtitle = artistName,
        thumbnail = thumbnailUrl,
        primaryActions =
            songPrimaryActions(
                onDismiss = onDismiss,
                onStartRadio = onStartRadio,
                onAddToPlaylist =
                    onAddToPlaylist?.let {
                        {
                            it()
                            onDismiss()
                        }
                    },
                onShare = onShare,
                onAltShare = onAltShare,
            ),
        actionGroups =
            listOf(
                listOfNotNull(
                    dislikeMenuAction(isDisliked, onDismiss, onDislike),
                    onRemoveFromQueue?.let {
                        action(MuzikIcons.Outlined.Delete, stringResource(Res.string.remove_from_queue), onDismiss, it)
                    },
                ) + queueActions(onDismiss, onPlayNext, onAddToQueue),
                listOfNotNull(downloadAction(MediaDownloadState.NOT_DOWNLOADED, 0, false, onDismiss, onDownload), deleteUploadAction),
                listOfNotNull(
                    onNavigateToListenTogether?.takeIf { Settings.shared.listenTogetherEnabled }?.let {
                        action(MuzikIcons.Outlined.Group, stringResource(Res.string.listen_together), onDismiss, it)
                    },
                ),
                listOfNotNull(
                    artistNavigationMenuAction(artists, onGoToArtist, onDismiss),
                    albumNavigationMenuAction(onDismiss, onGoToAlbum.takeUnless { albumName.isNullOrBlank() }, albumName),
                ),
                listOfNotNull(
                    onRefetch?.let {
                        action(
                            MuzikIcons.Outlined.Refresh,
                            stringResource(Res.string.refetch),
                            onDismiss,
                            it,
                            stringResource(Res.string.refetch_desc),
                        )
                    },
                    onShowInfo?.let {
                        PlayerMenuAction(
                            MuzikIcons.Outlined.Info,
                            stringResource(Res.string.details),
                            stringResource(Res.string.details_desc),
                            onClick = it,
                        )
                    },
                ),
            ),
    )
