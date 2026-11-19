/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.desktop.alarm

import com.muzik.desktop.state.playback.PlayerState
import com.muzik.shared.alarm.MusicAlarmCodec
import com.muzik.shared.alarm.MusicAlarmEntry
import com.muzik.shared.alarm.isScheduled
import com.muzik.shared.alarm.nextAlarmTriggerAt
import com.muzik.shared.db.MusicDatabase
import com.muzik.shared.db.entities.toSongItem
import com.muzik.shared.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import org.koin.core.context.GlobalContext
import kotlin.time.Clock

/**
 * Desktop implementation of the Android app's music alarm.
 *
 * Ticks every 20 seconds, keeps each entry's next trigger up to date and starts
 * playlist playback (optionally shuffled) when an alarm fires. Alarms missed
 * while the app was closed are skipped, matching Android behaviour.
 */
object MusicAlarmEngine {
    private const val TICK_MILLIS = 20_000L
    private const val FIRE_WINDOW_MILLIS = 90_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch {
            while (isActive) {
                runCatching { tick() }
                delay(TICK_MILLIS)
            }
        }
    }

    private suspend fun tick() {
        val settings = Settings.shared
        val alarms = MusicAlarmCodec.decode(settings.alarmEntriesJson)
        if (alarms.none { it.isScheduled() }) return

        val timeZone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toEpochMilliseconds()
        var changed = false
        val refreshed =
            alarms.map { alarm ->
                if (!alarm.isScheduled()) return@map alarm
                var next = alarm.nextTriggerAt
                if (next <= 0L || next < now - FIRE_WINDOW_MILLIS) {
                    // Missing schedule or a trigger missed while the app was closed: re-arm for tomorrow.
                    next = nextAlarmTriggerAt(alarm.hour, alarm.minute, timeZone, now)
                    changed = true
                }
                if (now >= next && now - next < FIRE_WINDOW_MILLIS) {
                    fireAlarm(alarm)
                    next = nextAlarmTriggerAt(alarm.hour, alarm.minute, timeZone, Clock.System.now().toEpochMilliseconds())
                    changed = true
                }
                alarm.copy(nextTriggerAt = next)
            }
        if (changed) {
            settings.alarmEntriesJson = MusicAlarmCodec.encode(refreshed)
        }
    }

    private suspend fun fireAlarm(alarm: MusicAlarmEntry) {
        val database = GlobalContext.get().get<MusicDatabase>()
        val songs =
            database
                .dao()
                .playlistSongs(alarm.playlistId)
                .first()
                .map { it.toSongItem() }
                .filter { !it.isUnavailable }
        if (songs.isEmpty()) return
        withContext(Dispatchers.Main) {
            val queue = if (alarm.randomSong) songs.shuffled() else songs
            queue.firstOrNull { !it.isUnavailable }?.let { PlayerState.playTrack(it, queue) }
        }
    }
}
