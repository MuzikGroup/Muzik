package com.muzik.shared.utils

import kotlin.time.Clock

object MuzikClock {
    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
