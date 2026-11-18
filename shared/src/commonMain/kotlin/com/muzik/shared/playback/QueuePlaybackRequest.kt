package com.muzik.shared.playback

import com.muzik.shared.model.SongItem

data class QueuePlaybackRequest(
    val requestId: Long,
    val song: SongItem,
    val positionMs: Long = 0L,
    val resumePlayback: Boolean = true,
)
