/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.alarm

import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * A scheduled music alarm: at [hour]:[minute] local time, start playback of [playlistId].
 * Mirrors the Android app's music alarm so the feature is available on every platform.
 */
@Serializable
data class MusicAlarmEntry(
    val id: String,
    val enabled: Boolean = true,
    val hour: Int = 7,
    val minute: Int = 0,
    val playlistId: String = "",
    val randomSong: Boolean = false,
    /** Epoch millis of the next scheduled fire, or -1 when not scheduled. */
    val nextTriggerAt: Long = -1L,
)

object MusicAlarmCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(MusicAlarmEntry.serializer())

    fun encode(entries: List<MusicAlarmEntry>): String = json.encodeToString(serializer, entries)

    fun decode(raw: String?): List<MusicAlarmEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }
            .recoverCatching { legacyJsonArray(raw) }
            .getOrDefault(emptyList())
            .map {
                it.copy(
                    hour = it.hour.coerceIn(0, 23),
                    minute = it.minute.coerceIn(0, 59),
                )
            }
    }

    /** Accepts the Android app's org.json array format for cross-platform backup restores. */
    private fun legacyJsonArray(raw: String): List<MusicAlarmEntry> {
        val array = runCatching { json.decodeFromString(kotlinx.serialization.json.JsonArray.serializer(), raw) }
            .getOrNull() ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val item: JsonObject = element.jsonObject
                fun string(key: String): String? = (item[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                MusicAlarmEntry(
                    id = string("id")?.takeIf { it.isNotBlank() } ?: newId(),
                    enabled = string("enabled")?.toBooleanStrictOrNull() ?: true,
                    hour = (string("hour")?.toIntOrNull() ?: 7).coerceIn(0, 23),
                    minute = (string("minute")?.toIntOrNull() ?: 0).coerceIn(0, 59),
                    playlistId = string("playlistId").orEmpty(),
                    randomSong = string("randomSong")?.toBooleanStrictOrNull() ?: false,
                    nextTriggerAt = string("nextTriggerAt")?.toLongOrNull() ?: -1L,
                )
            }.getOrNull()
        }
    }

    fun newId(): String = buildString {
        val charset = "0123456789abcdef"
        repeat(32) { append(charset[kotlin.random.Random.nextInt(charset.length)]) }
    }
}

/** Returns the next epoch-millis occurrence of [hour]:[minute] strictly after now, in [timeZone]. */
fun nextAlarmTriggerAt(
    hour: Int,
    minute: Int,
    timeZone: TimeZone,
    now: Long = Clock.System.now().toEpochMilliseconds(),
): Long {
    val nowLocal = kotlinx.datetime.Instant.fromEpochMilliseconds(now).toLocalDateTime(timeZone)
    val today = LocalDateTime(
        nowLocal.year,
        nowLocal.monthNumber,
        nowLocal.dayOfMonth,
        hour.coerceIn(0, 23),
        minute.coerceIn(0, 59),
        0,
        0,
    )
    val todayMillis = today.toInstant(timeZone).toEpochMilliseconds()
    return if (todayMillis > now) todayMillis else todayMillis + 24 * 60 * 60 * 1000L
}

/** Recomputes the next trigger for every entry against [timeZone]. */
fun List<MusicAlarmEntry>.withRefreshedTriggers(timeZone: TimeZone): List<MusicAlarmEntry> =
    map { entry ->
        if (entry.enabled && entry.playlistId.isNotBlank()) {
            entry.copy(nextTriggerAt = nextAlarmTriggerAt(entry.hour, entry.minute, timeZone))
        } else {
            entry.copy(nextTriggerAt = -1L)
        }
    }

/** Formats an entry as "HH:mm" using 24-hour clock, matching the Android app. */
fun MusicAlarmEntry.clockLabel(): String = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

/** The minute-of-day used for ordering alarms in the settings list. */
val MusicAlarmEntry.timeOfDayMinutes: Int
    get() = hour * 60 + minute

/** True when the entry will actually fire. */
fun MusicAlarmEntry.isScheduled(): Boolean = enabled && playlistId.isNotBlank()
