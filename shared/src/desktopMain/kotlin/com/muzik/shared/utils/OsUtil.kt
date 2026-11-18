/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.utils

object OsUtil {
    val name: String = System.getProperty("os.name")
    private val normalizedName = name.lowercase()
    val isLinux: Boolean = normalizedName.contains("linux")
    val isMac: Boolean = normalizedName.contains("mac")
    val isWindows: Boolean = normalizedName.contains("win")
}
