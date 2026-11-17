package com.muzik.desktop.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

val innertubeJson = Json { ignoreUnknownKeys = true; isLenient = true }

// Package-level navigation helpers tuned to InnerTube's deep, shifting response
// shapes. Kept top-level so call sites stay terse without member imports.

fun obj(e: JsonElement?): JsonObject = (e as? JsonObject) ?: JsonObject(emptyMap())

fun arr(e: JsonElement?): JsonArray = (e as? JsonArray) ?: JsonArray(emptyList())

fun str(e: JsonElement?): String? = (e as? JsonPrimitive)?.content

/** Path steps: a String descends into an object key, an Int indexes an array. */
private fun descend(cur: JsonElement?, key: Any): JsonElement? = when (key) {
    is Int -> arr(cur).getOrNull(key)
    is String -> obj(cur)[key]
    else -> null
}

fun str(o: JsonObject?, vararg path: Any): String? {
    var cur = o as JsonElement?
    for (key in path) {
        cur = descend(cur, key)
        if (cur == null) return null
    }
    return str(cur)
}

fun strOrEmpty(o: JsonObject?, vararg path: Any): String = str(o, *path) ?: ""

fun objAt(o: JsonObject?, vararg path: Any): JsonObject {
    var cur = o as JsonElement?
    for (key in path) {
        cur = descend(cur, key)
        if (cur == null) return JsonObject(emptyMap())
    }
    return obj(cur)
}

fun arrAt(o: JsonObject?, vararg path: Any): JsonArray = arr(objAt(o, *path))

fun int(o: JsonObject?, vararg path: Any): Int = str(o, *path)?.toIntOrNull() ?: 0

/** Concatenates the text of all runs in a music text renderer node. */
fun runsText(o: JsonObject?, vararg path: Any): String =
    arrAt(o, *path).joinToString("") { run -> str(obj(run), "text") ?: "" }

/** First run object that carries a navigation endpoint (used to resolve artist/album pages). */
fun firstNavigableRun(o: JsonObject?, vararg path: Any): JsonObject {
    for (run in arrAt(o, *path)) {
        val ro = obj(run)
        if (ro.containsKey("browseEndpoint") || ro.containsKey("watchEndpoint")) return ro
    }
    return JsonObject(emptyMap())
}

/** Largest thumbnail URL from a musicThumbnailRenderer-style node. */
fun bestThumb(o: JsonObject?, vararg path: Any): String? =
    arrAt(o, *path)
        .mapNotNull { t ->
            val w = str(obj(t), "width")?.toIntOrNull() ?: 0
            val url = str(obj(t), "url") ?: return@mapNotNull null
            w to url
        }
        .maxByOrNull { it.first }
        ?.second
