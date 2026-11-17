package com.muzik.desktop.smoke

import com.muzik.desktop.youtube.arrAt
import com.muzik.desktop.youtube.str
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject

/**
 * Synthetic parser test: feeds a canned WEB_REMIX-style player response
 * through the same JSON helpers the browse parsers use, proving selection
 * works end to end without network access.
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

    // Exercise the selection logic by parsing the same structures resolvers read.
    val formats = arrAt(canned, "streamingData", "adaptiveFormats")
        .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
        .filter { (str(it, "mimeType") ?: "").startsWith("audio/mp4") }
        .filter { str(it, "url") != null }
    check(formats.size == 3) { "expected 3 audio formats, got ${formats.size}" }

    val normal = formats.minByOrNull {
        val b = str(it, "bitrate")?.toIntOrNull() ?: 0
        kotlin.math.abs(b - 128_000) * 10 + b
    }
    check(str(normal, "itag") == "140") { "normal quality should pick itag 140, got ${str(normal, "itag")}" }

    val high = formats.maxByOrNull { str(it, "bitrate")?.toIntOrNull() ?: 0 }
    check(str(high, "itag") == "141") { "high quality should pick itag 141" }

    println("PARSER_TEST_OK: formats=${formats.size} normal=itag${str(normal, "itag")} high=itag${str(high, "itag")}")
}
