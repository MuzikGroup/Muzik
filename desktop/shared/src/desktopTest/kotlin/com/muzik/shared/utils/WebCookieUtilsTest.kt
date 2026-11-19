package com.muzik.shared.utils

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebCookieUtilsTest {
    @Test
    fun deletesProfileInsideUserHome() {
        val userHome = Files.createTempDirectory("muzik-home")
        val profile = userHome.resolve(".local/share/Muzik/WebView").createDirectories()
        profile.resolve("cookie.db").createFile()

        deleteWebViewProfileDirectory(profile.toFile(), userHome.toFile())

        assertFalse(Files.exists(profile))
    }

    @Test
    fun rejectsProfilePathThroughSymlink() {
        val userHome = Files.createTempDirectory("muzik-home")
        val external = Files.createTempDirectory("muzik-external")
        val sentinel =
            external
                .resolve("WebView")
                .createDirectories()
                .resolve("cookie.db")
                .createFile()
        val localShare = userHome.resolve(".local/share").createDirectories()
        runCatching { Files.createSymbolicLink(localShare.resolve("Muzik"), external) }.getOrElse { return }
        val profile = localShare.resolve("Muzik/WebView")

        assertFailsWith<IllegalStateException> {
            deleteWebViewProfileDirectory(profile.toFile(), userHome.toFile())
        }
        assertTrue(Files.exists(sentinel))
    }
}
