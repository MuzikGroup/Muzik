package com.muzik.shared.repository

import com.muzik.shared.model.LyricsProvider
import com.muzik.shared.model.LyricsWithProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserLyricsPriorityTest {
    @Test
    fun userLyricsWinWithProvidersDisabledOrReordered() {
        val user = LyricsWithProvider("[00:00.00] My words", USER_LYRICS_PROVIDER)
        val provider = LyricsWithProvider("[00:01.00] Provider", "LrcLib")
        for (order in listOf(emptyList(), listOf(LyricsProvider.LRCLIB), LyricsProvider.userConfigurable.reversed())) {
            assertTrue(prefersLyrics(user, provider, order))
            assertFalse(prefersLyrics(provider, user, order))
        }
    }
}
