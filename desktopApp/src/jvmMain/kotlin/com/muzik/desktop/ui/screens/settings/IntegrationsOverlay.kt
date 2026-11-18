/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzik.desktop.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.muzik.desktop.auth.DesktopDiscordLogin
import com.muzik.desktop.state.app.AppState
import com.muzik.desktop.state.app.UiState
import com.muzik.desktop.state.playback.DiscordRpcState
import com.muzik.desktop.state.playback.ListenTogetherState
import com.muzik.desktop.state.playback.PlayerState
import com.muzik.desktop.ui.screens.DiscordLoginDialog
import com.muzik.shared.discord.openDiscordUrl
import com.muzik.shared.settings.Settings
import com.muzik.shared.ui.components.Dialog
import com.muzik.shared.ui.components.PopupContent
import com.muzik.shared.ui.settings.IntegrationPlaybackState
import com.muzik.shared.ui.settings.SettingsPlatform
import com.muzik.shared.ui.settings.SharedIntegrationsHost
import com.muzik.shared.ui.settings.SharedIntegrationsScreen

@Composable
fun IntegrationsOverlay(colorScheme: ColorScheme) {
    val discordLogin = DesktopDiscordLogin.flow
    val playbackSpeed by PlayerState.player.playbackSpeed.collectAsState()
    val playbackDuration by PlayerState.playbackDuration.collectAsState()

    Dialog(
        onDismissRequest = { UiState.showIntegrationsSettings = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .padding(16.dp)
                    .widthIn(max = 1180.dp)
                    .fillMaxWidth()
                    .heightIn(max = 800.dp)
                    .fillMaxHeight(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            SharedIntegrationsScreen(
                host =
                    SharedIntegrationsHost(
                        platform = SettingsPlatform.DESKTOP,
                        onClose = { UiState.showIntegrationsSettings = false },
                        playbackPosition = PlayerState.playbackPosition,
                        currentPositionMs = PlayerState::currentMediaPositionMs,
                        playback =
                            PlayerState.currentTrack?.let { song ->
                                IntegrationPlaybackState(
                                    song = song,
                                    artworkUrl = PlayerState.currentArtworkUrl,
                                    artistArtworkUrl = song.artists.firstOrNull()?.thumbnail,
                                    durationMs = playbackDuration,
                                    isPlaying = PlayerState.isPlaying,
                                    playbackSpeed = playbackSpeed,
                                )
                            },
                        onStartDiscordLogin = discordLogin::start,
                        discordLoginInProgress = discordLogin.session != null || discordLogin.exchanging,
                        onDiscordSettingsChanged = {
                            DiscordRpcState.discordRpcEnabled = Settings.shared.enableDiscordIntegration
                            DiscordRpcState.updateDiscordRpc()
                        },
                        deviceConnect = rememberDeviceConnectSettingsHost(),
                        listenTogetherManager = ListenTogetherState.listenTogetherManager,
                        onOpenUrl = { openDiscordUrl(it, null) },
                        onCopyText = { _, text -> AppState.copyToClipboard(text) },
                    ),
            )
        }
    }

    PopupContent(discordLogin.session) { session ->
        DiscordLoginDialog(
            session = session,
            onDismissRequest = discordLogin::cancel,
            onComplete = discordLogin::cancel,
        )
    }
}
