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
 * Muzik therefore walks a chain of tokenless playback profiles and keeps the
 * first one that yields a directly playable audio URL — the same
 * multi-profile, tokenless-first philosophy measured by the NewPipeExtractor
 * community (GPL-3.0), which Muzik's own extractor is built on:
 *
 *  1. VISIONOS 0.1 — watch-config visitor identity; fast tokenless audio.
 *  2. IOS 20.10.4 — Apple device identity; usually returns direct URLs.
 *  3. ANDROID_VR 1.62 — headset identity; independent of the two above.
 *  4. TV HTML5 Samsung — tokenless direct audio that unlocks after a short
 *     pre-roll window, so a 403 on first touch triggers bounded retries.
 *
 * If a profile answers with a stale-session style error ("page needs to be
 * reloaded") the cached visitor identity is invalidated once and the chain
 * retried. If every profile fails (datacenter IPs, VPN exits, or YouTube
 * policy shifts), the caller receives a clear error the player surfaces in
 * the UI.
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
        val extraContext: Map<String, JsonPrimitive> = emptyMap(),
        val prerollRetry: Boolean = false,
        val musicOrigin: Boolean = true,
    )

    private fun prim(v: String) = JsonPrimitive(v)

    private val profiles = listOf(
        Profile(
            id = "VISIONOS_0_1",
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            extraContext = mapOf(
                "osName" to prim("VISION_OS"),
                "osVersion" to prim("1.3"),
                "deviceMake" to prim("Apple"),
                "deviceModel" to prim("RealityDevice14,1"),
                "platform" to prim("MOBILE"),
            ),
            musicOrigin = true,
        ),
        Profile(
            id = "IOS_20_10_4",
            clientName = "IOS",
            clientVersion = "20.10.4",
            clientId = "5",
            userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; iOS 18.1.0; gzip)",
            extraContext = mapOf(
                "deviceMake" to prim("Apple"),
                "deviceModel" to prim("iPhone16,2"),
                "osName" to prim("iPhone"),
                "osVersion" to prim("18.1.0.22B83"),
            ),
            musicOrigin = false,
        ),
        Profile(
            id = "ANDROID_VR_1_62",
            clientName = "ANDROID_VR",
            clientVersion = "1.62.27",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; " +
                "Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extraContext = mapOf(
                "androidSdkVersion" to prim("31"),
                "osName" to prim("Android"),
                "osVersion" to prim("12"),
            ),
            musicOrigin = false,
        ),
        Profile(
            id = "TVHTML5_SAMSUNG",
            clientName = "TVHTML5",
            clientVersion = "5.20260707",
            clientId = "7",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 " +
                "(KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
            extraContext = mapOf(
                "osName" to prim("Tizen"),
                "osVersion" to prim("2.4.0"),
                "deviceMake" to prim("Samsung"),
                "deviceModel" to prim("UKS9800"),
            ),
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
        for (round in 0..1) {
            for ((i, profile) in profiles.withIndex()) {
                // Pace the requests: firing the whole chain back-to-back reads
                // as automation to YouTube and triggers bot-gating.
                if (i > 0 || round > 0) delay(500)
                try {
                    val candidate = playerRequest(videoId, profile, highQuality, failures)
                    if (candidate != null) return candidate
                } catch (e: Exception) {
                    failures.add("${profile.id}: ${e.message ?: e.javaClass.simpleName}")
                }
            }
            // Hard bot-gates ("Sign in to confirm you're not a bot") are IP
            // reputation based — retrying will not help, so fail fast.
            val gated = failures.any {
                it.contains("Sign in to confirm", ignoreCase = true) ||
                    it.contains("LOGIN_REQUIRED", ignoreCase = true)
            }
            if (gated || round == 1) break
            // Otherwise a stale visitor identity may be the cause; refresh it
            // once and give the chain a calm second pass.
            InnerTube.invalidateVisitor()
        }
        val gated = failures.any { it.contains("Sign in to confirm", ignoreCase = true) }
        val headline = if (gated) {
            "YouTube is bot-gating this network. Try again in a few minutes, or switch networks."
        } else {
            "No playback profile succeeded."
        }
        throw NoPlayableStream("$headline\n${failures.joinToString("\n")}")
    }

    class NoPlayableStream(detail: String) : Exception(
        "No playback profile succeeded.\n$detail"
    )

    private suspend fun playerRequest(videoId: String, p: Profile, highQuality: Boolean, failures: MutableList<String>): StreamInfo? {
        val visitor = InnerTube.visitorData()
        val body = buildJsonObject {
            put("context", buildJsonObject {
                put("client", buildJsonObject {
                    put("clientName", JsonPrimitive(p.clientName))
                    put("clientVersion", JsonPrimitive(p.clientVersion))
                    put("hl", JsonPrimitive("en"))
                    put("gl", JsonPrimitive("US"))
                    put("utcOffsetMinutes", JsonPrimitive(0))
                    p.extraContext.forEach { (k, v) -> put(k, v) }
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
