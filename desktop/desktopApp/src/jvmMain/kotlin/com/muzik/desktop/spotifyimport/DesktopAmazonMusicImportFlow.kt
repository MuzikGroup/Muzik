package com.muzik.desktop.spotifyimport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.muzik.shared.amazonmusicimport.AmazonMusicImportManager
import com.muzik.shared.db.MusicDatabase
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.import_amazon_picker_description
import com.muzik.shared.generated.resources.import_amazon_picker_title
import com.muzik.shared.generated.resources.import_amazon_selected_count
import com.muzik.shared.generated.resources.import_provider_amazon
import com.muzik.shared.generated.resources.import_status_fetching_metadata
import com.muzik.shared.generated.resources.import_status_fetching_tracks
import com.muzik.shared.generated.resources.liked_songs
import com.muzik.shared.generated.resources.spotify_import_error_no_playlists
import com.muzik.shared.generated.resources.spotify_import_error_sign_in_failed
import com.muzik.shared.playlistimport.ImportSourceType
import com.muzik.shared.spotifyimport.SpotifyImportManager
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.util.logging.Logger

@Composable
@Suppress("FunctionName")
internal fun DesktopAmazonMusicImportFlow(
    database: MusicDatabase,
    onDismissRequest: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val providerName = stringResource(Res.string.import_provider_amazon)
    val fetchingMetadata = stringResource(Res.string.import_status_fetching_metadata)
    val fetchingTracks = stringResource(Res.string.import_status_fetching_tracks)
    val noPlaylists = stringResource(Res.string.spotify_import_error_no_playlists)
    val signInFailed = stringResource(Res.string.spotify_import_error_sign_in_failed)
    val logger = remember { Logger.getLogger("AmazonMusicImport") }
    val manager = remember { AmazonMusicImportManager() }
    var amazonPlaylists by remember { mutableStateOf(emptyList<AmazonMusicImportManager.LoadedPlaylist>()) }

    DesktopAccountImportFlow(
        database = database,
        providerName = providerName,
        provider = ImportSourceType.AMAZON,
        logger = logger,
        authenticationTitle = fetchingMetadata,
        playlistLoadingTitle = fetchingTracks,
        noPlaylists = noPlaylists,
        signInFailed = signInFailed,
        authenticationFailureLog = "Amazon Music authentication failed",
        playlistFailureLog = "Failed to load selected Amazon Music playlists",
        onDismissRequest = onDismissRequest,
        onStatus = onStatus,
        onClose = manager::close,
        loginContent = { onCredentials ->
            AmazonMusicLoginDialog(onDismissRequest) { cookie, host ->
                onCredentials(
                    { manager.authenticate(cookie, host) },
                    {
                        amazonPlaylists =
                            manager.loadPlaylists(likedSongsName = getString(Res.string.liked_songs))
                        amazonPlaylists.map { DesktopImportPlaylist(it.id, it.name, it.image, it.trackCount) }
                    },
                )
            }
        },
        playlistPicker = { playlists, selectedIds, onSelectionChange, onReview, dismiss ->
            DesktopAccountPlaylistPickerDialog(
                title = Res.string.import_amazon_picker_title,
                description = Res.string.import_amazon_picker_description,
                selectedCount = Res.string.import_amazon_selected_count,
                playlists = playlists,
                selectedIds = selectedIds,
                onSelectionChange = onSelectionChange,
                onReview = onReview,
                onDismissRequest = dismiss,
            )
        },
        loadSelectedPlaylists = { selected, onProgress ->
            buildList {
                for ((index, option) in selected.withIndex()) {
                    val playlist = amazonPlaylists.firstOrNull { it.id == option.id } ?: continue
                    onProgress(index + 1, selected.size)
                    val importedData =
                        manager.loadPlaylist(playlist, likedSongsName = getString(Res.string.liked_songs)) { loaded, total ->
                            onProgress(loaded, total ?: 0)
                        }
                    add(
                        SpotifyImportManager.LoadedPlaylist(
                            uri = playlist.id,
                            name = playlist.name,
                            image = playlist.image,
                            tracks =
                                importedData.tracks.map {
                                    SpotifyImportManager.ImportTrack(
                                        it.title,
                                        it.artist,
                                        it.album,
                                        it.durationSeconds,
                                    )
                                },
                            reachedTrackLimit = false,
                        ),
                    )
                }
            }
        },
    )
}
