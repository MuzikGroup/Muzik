package com.muzik.desktop.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateInfo(
    val version: String,
    val notesUrl: String,
    val assetUrl: String?,
)

/**
 * Checks GitHub releases for a newer Muzik Desktop build and points the user
 * at the right installer for the running OS.
 */
object Updater {

    private const val RELEASES_API = "https://api.github.com/repos/MuzikGroup/MuzikDesktop/releases/latest"

    suspend fun latestVersion(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val text = java.net.URL(RELEASES_API).readText()
            val root = Json.parseToJsonElement(text).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return@withContext null
            val assets = root["assets"]?.jsonArray ?: return@withContext UpdateInfo(tag, releaseUrl(tag), null)
            val assetForOs = pickAsset(assets.map { it.jsonObject })
            UpdateInfo(tag, releaseUrl(tag), assetForOs)
        } catch (_: Exception) {
            null
        }
    }

    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    fun releaseUrl(tag: String) = "https://github.com/MuzikGroup/MuzikDesktop/releases/tag/v$tag"

    private fun pickAsset(assets: List<JsonObject>): String? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val suffix = when {
            os.contains("win") -> ".msi"
            os.contains("mac") -> if (arch.contains("aarch64") || arch.contains("arm")) "-arm64.dmg" else ".dmg"
            else -> if (arch.contains("aarch64") || arch.contains("arm")) "_aarch64.deb" else "_amd64.deb"
        }
        return assets
            .mapNotNull { it["browser_download_url"]?.jsonPrimitive?.content }
            .firstOrNull { it.endsWith(suffix) }
    }
}
