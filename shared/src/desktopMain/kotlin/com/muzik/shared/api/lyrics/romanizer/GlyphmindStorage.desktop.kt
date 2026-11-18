/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzik.shared.api.lyrics.romanizer

import com.muzik.shared.utils.XdgUtils
import java.io.File

internal actual fun glyphmindStorageDirectory(): String = File(XdgUtils.getXdgDataHome(), "muzik/glyphmind").absolutePath

internal actual fun removeLegacyJapaneseDictionary() {
    File(XdgUtils.getXdgDataHome(), "muzik/japanese-dictionary").deleteRecursively()
}
