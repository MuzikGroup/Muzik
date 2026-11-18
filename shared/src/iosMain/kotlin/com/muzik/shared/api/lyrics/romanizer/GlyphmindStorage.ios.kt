/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.api.lyrics.romanizer

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

private fun appSupport(): String =
    requireNotNull(NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).firstOrNull() as? String)

@OptIn(ExperimentalForeignApi::class)
internal actual fun glyphmindStorageDirectory(): String {
    val path = "${appSupport()}/muzik/glyphmind"
    check(NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null))
    check(NSURL.fileURLWithPath(path).setResourceValue(true, NSURLIsExcludedFromBackupKey, error = null))
    return path
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun removeLegacyJapaneseDictionary() {
    val path = "${appSupport()}/muzik/japanese-dictionary"
    if (NSFileManager.defaultManager.fileExistsAtPath(path)) check(NSFileManager.defaultManager.removeItemAtPath(path, error = null))
}
