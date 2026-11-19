package com.muzik.desktop.state.playback

import com.muzik.shared.model.RepeatMode
import com.muzik.shared.playback.MusicQueueSnapshot
import com.muzik.shared.playback.StoredQueueSnapshotSerializer
import kotlinx.serialization.Serializable

@Serializable
data class PersistedPlaybackState(
    @Serializable(with = StoredQueueSnapshotSerializer::class)
    val queue: MusicQueueSnapshot,
    val positionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val revision: String? = null,
)
