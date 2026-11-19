package com.muzik.desktop.state.content

import com.muzik.shared.db.entities.PlaylistEntity
import com.muzik.shared.db.hasCompleteStoredSongs
import com.muzik.shared.model.SongItem
import kotlinx.coroutines.Job

internal fun hasCompleteStoredPlaylist(
    playlist: PlaylistEntity,
    songs: List<SongItem>,
): Boolean = playlist.hasCompleteStoredSongs(songs)

internal suspend fun awaitCompletePlaylist(
    loadJob: Job?,
    hasMore: () -> Boolean,
): Boolean {
    if (hasMore()) loadJob?.join()
    return !hasMore()
}
