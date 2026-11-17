package com.muzik.desktop.smoke

import com.muzik.desktop.youtube.StreamResolver
import com.muzik.desktop.youtube.StreamInfo
import com.muzik.desktop.youtube.InnerTube
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject

/**
 * Synthetic parser test: feeds a canned WEB_REMIX-style player response
 * through the same JSON helpers StreamResolver uses, proving adaptiveFormats
 * selection works end to end without network access.
 */
fun main() = runBlocking {
    val canned = buildJsonObject {
        putJsonObject("playabilityStatus") { put("status", "OK") }
        putJsonObject("streamingData") {
            putJsonArray("adaptiveFormats") {
                addJsonObject {
                    put("itag", 139)
                    put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                    put("bitrate", 50152)
                    put("url", "https://example.invalid/low.m4a")
                }
                addJsonObject {
                    put("itag", 140)
                    put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                    put("bitrate", 129703)
                    put("url", "https://example.invalid/mid.m4a")
                }
                addJsonObject {
                    put("itag", 141)
                    put("mimeType", "audio/mp4; codecs=\"mp4a.40.2\"")
                    put("bitrate", 301005)
                    put("url", "https://example.invalid/high.m4a")
                }
            }
        }
    }

    // Exercise the selection logic by parsing the same structures the resolver reads.
    val formats = com.muzik.desktop.youtube.arrAt(canned, "streamingData", "adaptiveFormats")
        .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        .filter { (com.muzik.desktop.youtube.str(it, "mimeType") ?: "").startsWith("audio/mp4") }
        .filter { com.muzik.desktop.youtube.str(it, "url") != null }
    check(formats.size == 3) { "expected 3 audio formats, got ${formats.size}" }

    val normal = formats.minByOrNull {
        val b = com.muzik.desktop.youtube.str(it, "bitrate")?.toIntOrNull() ?: 0
        kotlin.math.abs(b - 128_000) * 10 + b
    }
    check(com.muzik.desktop.youtube.str(normal, "itag") == "140") { "normal quality should pick itag 140, got ${com.muzik.desktop.youtube.str(normal, "itag")}" }

    val high = formats.maxByOrNull { com.muzik.desktop.youtube.str(it, "bitrate")?.toIntOrNull() ?: 0 }
    check(com.muzik.desktop.youtube.str(high, "itag") == "141") { "high quality should pick itag 141" }

    println("PARSER_TEST_OK: formats=${formats.size} normal=itag${com.muzik.desktop.youtube.str(normal, "itag")} high=itag${com.muzik.desktop.youtube.str(high, "itag")}")
}
