/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.ui.settings

import com.muzik.shared.alarm.MusicAlarmCodec
import com.muzik.shared.alarm.MusicAlarmEntry
import com.muzik.shared.alarm.withRefreshedTriggers
import com.muzik.shared.settings.Settings
import kotlinx.datetime.TimeZone

/** Formats an alarm trigger epoch as a short localized date-time like "Wed, 07:00". */
internal expect fun formatAlarmTriggerDateTime(epochMillis: Long): String?

/** Decodes the persisted alarm list from [Settings]. */
internal fun loadAlarms(settings: Settings): List<MusicAlarmEntry> =
    MusicAlarmCodec.decode(settings.alarmEntriesJson)

/** Applies [transform] to the persisted alarm list and re-arms every entry. */
internal fun persistAlarms(
    settings: Settings,
    transform: (List<MusicAlarmEntry>) -> List<MusicAlarmEntry>,
) {
    val updated = transform(loadAlarms(settings)).withRefreshedTriggers(TimeZone.currentSystemDefault())
    settings.alarmEntriesJson = MusicAlarmCodec.encode(updated)
}
