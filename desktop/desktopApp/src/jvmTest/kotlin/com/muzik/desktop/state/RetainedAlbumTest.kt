package com.muzik.desktop.state

import com.muzik.desktop.state.content.canRestoreAlbum
import com.muzik.innertubex.InnerTube
import com.muzik.innertubex.models.YouTubeLocale
import com.muzik.shared.model.AlbumItem
import com.muzik.shared.model.AlbumPage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetainedAlbumTest {
    @Test
    fun onlyMatchingLoadedAlbumsFromTheSameSessionCanBeRestored() {
        val page = AlbumPage(AlbumItem("album", "Album", null, emptyList()), emptyList())
        val session = InnerTube.SessionSnapshot()
        assertTrue(canRestoreAlbum(page, "album", session, session))
        assertFalse(canRestoreAlbum(page, "other", session, session))
        assertFalse(canRestoreAlbum(null, "album", session, session))
        assertFalse(canRestoreAlbum(page, "album", null, session))
        assertFalse(canRestoreAlbum(page, "album", session, session.copy(cookie = "other")))
        assertFalse(canRestoreAlbum(page, "album", session, session.copy(locale = YouTubeLocale(gl = "PL", hl = "pl"))))
    }
}
