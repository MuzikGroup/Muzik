package com.muzik.shared.youtube.playback

import com.muzik.shared.model.SongItem

fun interface LocalAudioSource {
    suspend fun resolve(
        videoId: String,
        song: SongItem?,
    ): PlaybackResource?
}
