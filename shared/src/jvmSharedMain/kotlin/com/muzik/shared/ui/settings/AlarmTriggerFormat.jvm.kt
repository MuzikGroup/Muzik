/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.ui.settings

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun formatAlarmTriggerDateTime(epochMillis: Long): String? =
    runCatching {
        DateTimeFormatter
            .ofPattern("EEE, HH:mm")
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }.getOrNull()
