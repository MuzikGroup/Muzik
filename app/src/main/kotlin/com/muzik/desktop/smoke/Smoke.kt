package com.muzik.desktop.smoke

import com.muzik.desktop.youtube.InnerTube
import com.muzik.desktop.youtube.SearchFilter
import com.muzik.desktop.youtube.StreamResolver
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Headless end-to-end smoke test of the desktop playback pipeline:
 * search -> stream resolution -> ranged download probe.
 * Run with: java -Djava.awt.headless=true -cp uber.jar com.muzik.desktop.smoke.SmokeKt
 */
suspend fun main() {
    println("== Muzik Desktop smoke ==")

    val visitor = InnerTube.visitorData()
    println("visitorData: ${if (visitor != null) "OK (${visitor.length} chars)" else "null"}")

    val page = InnerTube.search("adele hello", SearchFilter.SONGS)
    println("search songs: ${page.songs.size}")
    require(page.songs.isNotEmpty()) { "search returned no songs" }
    page.songs.take(3).forEach { println("  - ${it.title} | ${it.artist} | ${it.durationText}") }
    require(page.songs.any { it.title != "Unknown" }) { "search titles failed to parse" }

    val song = page.songs.first()
    println("radio extension: ${InnerTube.radio(song.videoId).size} tracks")

    val stream = StreamResolver.resolve(song.videoId, highQuality = false)
    println("resolved: profile=${stream.profileId} itag=${stream.itag} bitrate=${stream.bitrate} mime=${stream.mimeType}")

    val conn = URL(stream.url).openConnection() as HttpsURLConnection
    conn.setRequestProperty("Range", "bytes=0-131071")
    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
    conn.connect()
    val code = conn.responseCode
    val type = conn.contentType ?: ""
    val body = conn.inputStream.use { it.readNBytes(200_000) }
    println("stream probe: HTTP $code | content-type=$type | got ${body.size} bytes")
    require(code == 206 || code == 200) { "stream probe failed: $code" }
    require(body.size > 65_536) { "stream returned too few bytes: ${body.size}" }
    // fMP4/AAC starts with a valid box header ('ftyp' within the first bytes)
    val ftyp = body.copyOfRange(0, minOf(64, body.size)).decodeToString().contains("ftyp")
    println("ftyp box present: $ftyp")

    println("SMOKE_OK")
}
