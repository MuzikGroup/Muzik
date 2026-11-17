package com.muzik.desktop.youtube

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Playback stream resolver.
 *
 * YouTube now gates unauthenticated media behind bot checks on many clients.
 * Following the measured, tokenless-first philosophy documented by
 * MetrolistGroup's innertubex (GPL-3.0), Muzik tries a chain of profiles that
 * passed sustained ranged playback in their November 2026 device benchmarks:
 *
 *  1. VISIONOS 0.1 (watch-config visitor identity) — fastest tokenless audio.
 *  2. TV HTML5 Samsung — tokenless direct audio; media unlocks after the
 *     pre-roll window, so a 403 on first touch triggers bounded retries.
 *
 * If every profile fails (datacenter IPs, VPN exits, or YouTube policy shifts),
 * the caller receives a clear error and the player surfaces it in the UI.
 */
object StreamResolver {

    private val http = HttpClient(io.ktor.client.engine.cio.CIO) {
        expectSuccess = false
    }

    data class Profile(
        val id: String,
        val clientName: String,
        val clientVersion: String,
        val clientId: String,
        val userAgent: String,
        val needsVisitorData: Boolean = false,
        val prerollRetry: Boolean = false,
        val musicOrigin: Boolean = true,
    )

    // Exact identities mirror innertubex PlaybackClientCatalog / YouTubeClient.
    private val profiles = listOf(
        Profile(
            id = "VISIONOS_0_1",
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            needsVisitorData = true,
            musicOrigin = true,
        ),
        Profile(
            id = "TVHTML5_SAMSUNG",
            clientName = "TVHTML5",
            clientVersion = "5.20260707",
            clientId = "7",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 " +
                "(KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
            prerollRetry = true,
            musicOrigin = false,
        ),
    )

    data class Resolution(
        val stream: StreamInfo,
        val attempts: List<String>,
    )

    /** Resolves a playable AAC (audio/mp4) stream for [videoId]. Throws [NoPlayableStream] when all profiles fail. */
    suspend fun resolve(videoId: String, highQuality: Boolean): StreamInfo {
        val failures = mutableListOf<String>()
        for (profile in profiles) {
            try {
                val candidate = playerRequest(videoId, profile, highQuality, failures)
                if (candidate != null) return candidate
            } catch (e: Exception) {
                failures.add("${profile.id}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        throw NoPlayableStream(failures.joinToString("\n"))
    }

    class NoPlayableStream(detail: String) : Exception(
        "No tokenless playback profile succeeded.\n$detail"
    )

    private suspend fun playerRequest(videoId: String, p: Profile, highQuality: Boolean, failures: MutableList<String>): StreamInfo? {
        val visitor = if (p.needsVisitorData) InnerTube.visitorData() else InnerTube.visitorData()
        val body = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", JsonPrimitive(p.clientName))
                    put("clientVersion", JsonPrimitive(p.clientVersion))
                    put("hl", JsonPrimitive("en"))
                    put("gl", JsonPrimitive("US"))
                    put("utcOffsetMinutes", JsonPrimitive(0))
                    if (p.musicOrigin) {
                        put("osName", JsonPrimitive("VISION_OS"))
                        put("osVersion", JsonPrimitive("1.3"))
                        put("deviceMake", JsonPrimitive("Apple"))
                        put("deviceModel", JsonPrimitive("RealityDevice14,1"))
                        put("platform", JsonPrimitive("MOBILE"))
                    } else {
                        put("osName", JsonPrimitive("Tizen"))
                        put("osVersion", JsonPrimitive("2.4.0"))
                        put("deviceMake", JsonPrimitive("Samsung"))
                        put("deviceModel", JsonPrimitive("UKS9800"))
                    }
                    visitor?.let { put("visitorData", JsonPrimitive(it)) }
                })
            })
            put("videoId", JsonPrimitive(videoId))
            put("contentCheckOk", JsonPrimitive(true))
            put("racyCheckOk", JsonPrimitive(true))
            if (!p.musicOrigin) put("videoCheckOk", JsonPrimitive(true))
        }

        val host = if (p.musicOrigin) "https://music.youtube.com" else "https://www.youtube.com"
        val response = http.post("$host/youtubei/v1/player?prettyPrint=false") {
            headers {
                append(HttpHeaders.UserAgent, p.userAgent)
                append("X-YouTube-Client-Name", p.clientId)
                append("X-YouTube-Client-Version", p.clientVersion)
                visitor?.let { append("X-Goog-Visitor-Id", it) }
                if (p.musicOrigin) {
                    append(HttpHeaders.Origin, "https://music.youtube.com")
                    append(HttpHeaders.Referrer, "https://music.youtube.com/")
                }
            }
            setBody(body.toString())
        }
        val root = obj(innertubeJson.parseToJsonElement(response.bodyAsText().ifEmpty { "{}" }))

        val status = str(root, "playabilityStatus", "status")
        val reason = str(root, "playabilityStatus", "reason") ?: ""
        if (status != "OK") {
            failures.add("${p.id}: $status${if (reason.isNotBlank()) " — $reason" else ""}")
            return null
        }

        val formats = arrAt(root, "streamingData", "adaptiveFormats")
            .mapNotNull { (it as? JsonObject) }
            .filter { f -> (str(f, "mimeType") ?: "").startsWith("audio/mp4") }
            .filter { f -> str(f, "url") != null }

        if (formats.isEmpty()) {
            failures.add("${p.id}: no direct audio/mp4 formats (ciphered or SABR-only response)")
            return null
        }

        val best = if (highQuality) {
            formats.maxByOrNull { str(it, "bitrate")?.toIntOrNull() ?: 0 }
        } else {
            // Prefer ~128 kbps for predictable data use; fall back to lowest.
            formats.minByOrNull {
                val b = str(it, "bitrate")?.toIntOrNull() ?: 0
                kotlin.math.abs(b - 128_000) * 10 + b
            }
        } ?: formats.first()

        val url = str(best, "url")!!
        val itag = str(best, "itag")?.toIntOrNull() ?: 0
        val bitrate = str(best, "bitrate")?.toIntOrNull() ?: 0

        val playable = if (p.prerollRetry) {
            waitOutPreroll(url, p.userAgent, failures, p.id)
        } else {
            probe(url, p.userAgent)
        }
        if (!playable) return null

        return StreamInfo(
            url = url,
            itag = itag,
            bitrate = bitrate,
            mimeType = str(best, "mimeType") ?: "audio/mp4",
            profileId = p.id,
        )
    }

    /** TV Samsung profile: media is forbidden until the pre-roll window passes; retry bounded. */
    private suspend fun waitOutPreroll(url: String, ua: String, failures: MutableList<String>, profileId: String): Boolean {
        val maxAttempts = 5
        val backoffMs = 7_000L
        repeat(maxAttempts) { attempt ->
            if (probe(url, ua)) return true
            failures.add("$profileId: media 403 on attempt ${attempt + 1}/$maxAttempts (pre-roll window)")
            delay(backoffMs)
        }
        return false
    }

    private suspend fun probe(url: String, ua: String): Boolean {
        return try {
            val resp: HttpResponse = http.get(url) {
                headers {
                    append(HttpHeaders.Range, "bytes=0-65535")
                    append(HttpHeaders.UserAgent, ua)
                }
            }
            resp.status.value == 200 || resp.status.value == 206
        } catch (_: Exception) {
            false
        }
    }
}
