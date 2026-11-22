/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.api.lyrics

import com.muzik.innertubex.models.YouTubeClient
import kotlinx.serialization.json.JsonObject

data class YouTubeTranscriptWord(
    val text: String,
    val startMs: Long,
    val durationMs: Long = 0L,
)

data class YouTubeTranscriptCue(
    val text: String,
    val startMs: Long,
    val durationMs: Long,
    val words: List<YouTubeTranscriptWord>? = null,
)

interface YouTubeLyricsApi {
    suspend fun watchNext(videoId: String): JsonObject

    suspend fun browse(browseId: String): JsonObject

    suspend fun browse(
        client: YouTubeClient,
        browseId: String,
    ): JsonObject = browse(browseId)

    suspend fun player(videoId: String): JsonObject

    suspend fun transcript(videoId: String): List<YouTubeTranscriptCue>

    suspend fun getText(url: String): String
}
