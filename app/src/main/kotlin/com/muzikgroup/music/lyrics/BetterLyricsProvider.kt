/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzikgroup.music.lyrics

import android.content.Context
import com.muzikgroup.music.betterlyrics.BetterLyrics
import com.muzikgroup.music.constants.EnableBetterLyricsKey
import com.muzikgroup.music.utils.dataStore
import com.muzikgroup.music.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        context: Context,
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)
}
