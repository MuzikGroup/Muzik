/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.desktop.ui.screens

import androidx.compose.runtime.Composable
import com.muzik.desktop.state.playback.MuzikLinkState
import com.muzik.shared.ui.link.MuzikLinkScreen as SharedMuzikLinkScreen

@Composable
fun MuzikLinkScreen(manager: com.muzik.shared.listentogether.MuzikLinkManager) {
    SharedMuzikLinkScreen(manager = manager)
}
