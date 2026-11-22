package com.muzik.shared.youtube.playback

import com.muzik.innertubex.extraction.AudioQuality
import com.muzik.innertubex.extraction.ContentHints
import com.muzik.shared.model.SongItem
import com.muzik.shared.playback.PlaybackFailureCategory

interface StreamResolver {
    suspend fun resolve(
        videoId: String,
        hints: ContentHints = ContentHints(),
        audioQuality: AudioQuality = AudioQuality.AUTO,
        sourceSong: SongItem? = null,
    ): PlaybackResource

    suspend fun refresh(
        videoId: String,
        hints: ContentHints = ContentHints(),
        audioQuality: AudioQuality = AudioQuality.AUTO,
        sourceSong: SongItem? = null,
    ): PlaybackResource

    suspend fun resolveRemote(
        videoId: String,
        hints: ContentHints,
        audioQuality: AudioQuality,
        sourceSong: SongItem? = null,
        refresh: Boolean = false,
        excludedProfiles: Set<String> = emptySet(),
    ): PlaybackResource =
        if (refresh) refresh(videoId, hints, audioQuality, sourceSong) else resolve(videoId, hints, audioQuality, sourceSong)

    suspend fun prewarm() {}

    suspend fun clearPlaybackFailures(videoId: String) {}

    suspend fun invalidateNetworkScope(state: PlaybackNetworkState) {}

    /** Drops outage backoff tripped while the platform restricted background network access. */
    suspend fun resetExtractionBackoff() {}

    suspend fun invalidateAttestation() {}

    suspend fun refreshPlayerConfigAfterStreamRejection(): Boolean = false

    suspend fun close() {}

    suspend fun reportPlaybackFailure(
        videoId: String,
        clientName: String?,
        responseCode: Int?,
        profileId: String? = null,
        hints: ContentHints = ContentHints(),
        category: PlaybackFailureCategory = PlaybackFailureCategory.UNKNOWN,
    ) {}

    suspend fun reportPlaybackSuccess(
        profileId: String?,
        hints: ContentHints = ContentHints(),
    ) {}
}
