package com.muzik.shared.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.muzik.shared.settings.Settings

enum class HapticEvent {
    PRESS,
    TOGGLE_ON,
    TOGGLE_OFF,
    CONFIRM,
    REJECT,
    DRAG_START,
    DRAG_SNAP,
    LONG_PRESS,
    SEGMENT_TICK,
    DICE_ROLL,
}

class MuzikHaptics(
    private val enabled: () -> Boolean,
    private val platformFeedback: (HapticEvent) -> Unit,
) {
    fun perform(event: HapticEvent) {
        if (enabled()) platformFeedback(event)
    }

    fun toggle(checked: Boolean) = perform(if (checked) HapticEvent.TOGGLE_ON else HapticEvent.TOGGLE_OFF)
}

@Composable
fun rememberMuzikHaptics(): MuzikHaptics {
    val platformFeedback = rememberPlatformHaptics()
    return remember(platformFeedback) { MuzikHaptics({ Settings.shared.enableHapticFeedback }, platformFeedback) }
}

@Composable
internal expect fun rememberPlatformHaptics(): (HapticEvent) -> Unit

@Composable
fun MuzikHapticsProvider(content: @Composable () -> Unit) {
    val haptics = rememberMuzikHaptics()
    val feedback =
        remember(haptics) {
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    haptics.perform(hapticFeedbackType.toHapticEvent())
                }
            }
        }
    CompositionLocalProvider(LocalHapticFeedback provides feedback, content = content)
}

internal fun HapticFeedbackType.toHapticEvent(): HapticEvent =
    when (this) {
        HapticFeedbackType.LongPress -> HapticEvent.LONG_PRESS

        HapticFeedbackType.ToggleOn -> HapticEvent.TOGGLE_ON

        HapticFeedbackType.ToggleOff -> HapticEvent.TOGGLE_OFF

        HapticFeedbackType.Confirm -> HapticEvent.CONFIRM

        HapticFeedbackType.Reject -> HapticEvent.REJECT

        HapticFeedbackType.GestureThresholdActivate -> HapticEvent.DRAG_SNAP

        HapticFeedbackType.TextHandleMove,
        HapticFeedbackType.SegmentTick,
        HapticFeedbackType.SegmentFrequentTick,
        -> HapticEvent.SEGMENT_TICK

        else -> HapticEvent.PRESS
    }
