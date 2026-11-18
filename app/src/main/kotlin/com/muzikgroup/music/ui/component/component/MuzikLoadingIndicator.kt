/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzikgroup.music.ui.component.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Muzik branded loading indicator: the letter M logo travels in a circular
 * motion along a faint track, replacing the generic Material spinners.
 *
 * Renders inside whatever exact size the caller provides (e.g.
 * `Modifier.size(24.dp)`); when the caller gives no explicit size it falls
 * back to [DEFAULT_SIZE]. On large sizes the M rides on a round badge, on
 * small sizes the bare letter M orbits the track so it stays legible.
 */
@Composable
fun MuzikLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    label: String? = null,
) {
    val sized =
        if (label != null) {
            modifier.semantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            }
        } else {
            modifier
        }

    BoxWithConstraints(
        modifier = sized,
        contentAlignment = Alignment.Center,
    ) {
        val side =
            when {
                constraints.hasBoundedWidth &&
                    constraints.minWidth == constraints.maxWidth &&
                    constraints.minWidth > 0 -> (constraints.minWidth / LocalDensity.current.density).dp
                constraints.hasBoundedHeight &&
                    constraints.minHeight == constraints.maxHeight &&
                    constraints.minHeight > 0 -> (constraints.minHeight / LocalDensity.current.density).dp
                else -> DEFAULT_SIZE
            }

        val trackStroke = if (side >= 24.dp) maxOf(2.dp, side * 0.08f) else 2.dp

        val orbit = rememberInfiniteTransition(label = "muzikLoadingOrbit")
        val angle by orbit.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = ORBIT_PERIOD_MS, easing = LinearEasing),
                ),
            label = "muzikLoadingOrbitAngle",
        )

        // Faint circular track the M travels along.
        Box(
            modifier =
                Modifier
                    .size(side)
                    .border(trackStroke, color.copy(alpha = TRACK_ALPHA), CircleShape),
        )

        // The M logo orbits the track and always stays upright.
        Box(
            modifier =
                Modifier
                    .size(side)
                    .graphicsLayer { rotationZ = angle },
        ) {
            val orbitModifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { rotationZ = -angle }

            if (side >= BADGE_THRESHOLD) {
                val badge = side * BADGE_FRACTION
                Box(
                    modifier =
                        orbitModifier
                            .size(badge)
                            .background(color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "M",
                        color = logoColorOn(color),
                        fontSize = (badge.value * 0.56f).sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.sp,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = "M",
                    color = color,
                    fontSize = (side.value * 0.6f).sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    modifier = orbitModifier,
                )
            }
        }
    }
}

private const val ORBIT_PERIOD_MS = 1200
private const val TRACK_ALPHA = 0.22f
private const val BADGE_FRACTION = 0.44f
private val DEFAULT_SIZE = 44.dp
private val BADGE_THRESHOLD = 40.dp

/** Picks an M color that stays readable on the orbiting badge. */
private fun logoColorOn(badgeColor: Color): Color =
    if (badgeColor.luminance() > 0.55f) Color(0xFF141414) else Color.White
