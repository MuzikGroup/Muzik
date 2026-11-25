/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.ui.link

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzik.shared.listentogether.LinkConnectionState
import com.muzik.shared.listentogether.MuzikLinkManager
import com.muzik.shared.listentogether.MuzikLinkUiEvent
import com.muzik.shared.listentogether.network.LinkDeviceTypes
import com.muzik.shared.listentogether.network.LinkStatePayload
import com.muzik.shared.listentogether.network.PlaybackActions
import com.muzik.shared.ui.icons.MuzikIcons

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun deviceLabel(deviceType: String): String =
    when (deviceType) {
        LinkDeviceTypes.ANDROID -> "Android device"
        LinkDeviceTypes.DESKTOP -> "Computer"
        LinkDeviceTypes.IOS -> "iOS device"
        else -> "Device"
    }

@Composable
fun MuzikLinkScreen(manager: MuzikLinkManager) {
    val connectionState by manager.connectionState.collectAsState()
    val session by manager.linkSession.collectAsState()
    val remoteState by manager.remoteState.collectAsState()

    var joinCode by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        manager.uiEvents.collect { event ->
            notice =
                when (event) {
                    is MuzikLinkUiEvent.Error -> event.message
                    is MuzikLinkUiEvent.Info -> event.message
                }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Muzik Link",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Pair your devices: control playback anywhere and move it between them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            notice?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            when (val currentSession = session) {
                null -> {
                    if (connectionState == LinkConnectionState.CONNECTING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text("Connecting…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Start a link", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Creates a pairing code to enter on your other devices. This device becomes the player.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = { manager.createLink() },
                                enabled = connectionState != LinkConnectionState.CONNECTING,
                            ) {
                                Text("Create link")
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Join with a code", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = joinCode,
                                onValueChange = { joinCode = it.uppercase().take(6) },
                                label = { Text("Pairing code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FilledTonalButton(
                                onClick = { manager.joinLink(joinCode) },
                                enabled = joinCode.isNotBlank() && connectionState != LinkConnectionState.CONNECTING,
                            ) {
                                Text("Pair this device")
                            }
                        }
                    }
                }

                else -> {
                    val isPlayer = currentSession.playerDeviceId == currentSession.deviceId

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Pairing code",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                currentSession.pairingCode,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "Enter this code on your other devices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Devices", style = MaterialTheme.typography.titleMedium)
                            currentSession.devices.forEach { device ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            device.deviceName + if (device.deviceId == currentSession.deviceId) " (this device)" else "",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (device.isPlayer) FontWeight.Bold else FontWeight.Normal,
                                        )
                                        if (device.isPlayer) {
                                            Text(
                                                "Active player",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    if (!device.isPlayer && device.deviceId != currentSession.deviceId) {
                                        FilledTonalButton(onClick = { manager.handoffTo(device.deviceId) }) {
                                            Text("Play here")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!isPlayer) {
                        RemoteControlCard(remoteState, manager)
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "This device is the active player. Control it from your other paired devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { manager.leaveLink() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Leave link")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteControlCard(
    state: LinkStatePayload?,
    manager: MuzikLinkManager,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Remote control", style = MaterialTheme.typography.titleMedium)

            when (val current = state) {
                null -> {
                    Text(
                        "Waiting for the player to report playback…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    val track = current.currentTrack
                    val durationMs = track?.duration ?: 0L
                    Text(
                        track?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        track?.artist ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalIconButton(onClick = { manager.sendRemoteCommand(PlaybackActions.SKIP_PREV) }) {
                            Icon(MuzikIcons.Outlined.Replay, contentDescription = "Previous")
                        }
                        FilledTonalIconButton(onClick = {
                            if (current.isPlaying) {
                                manager.sendRemoteCommand(PlaybackActions.PAUSE, position = current.position)
                            } else {
                                manager.sendRemoteCommand(PlaybackActions.PLAY, position = current.position)
                            }
                        }) {
                            Icon(
                                if (current.isPlaying) MuzikIcons.Filled.Pause else MuzikIcons.Filled.PlayArrow,
                                contentDescription = if (current.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        FilledTonalIconButton(onClick = { manager.sendRemoteCommand(PlaybackActions.SKIP_NEXT) }) {
                            Icon(MuzikIcons.Filled.SkipNext, contentDescription = "Next")
                        }
                    }

                    if (durationMs > 0) {
                        var scrubbing by remember { mutableStateOf(false) }
                        var scrubValue by remember { mutableStateOf(0f) }
                        val displayPosition =
                            if (scrubbing) {
                                scrubValue
                            } else {
                                current.position.toFloat().coerceIn(0f, durationMs.toFloat())
                            }
                        Slider(
                            value = displayPosition,
                            onValueChange = {
                                scrubbing = true
                                scrubValue = it
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            onValueChangeFinished = {
                                manager.sendRemoteCommand(PlaybackActions.SEEK, position = scrubValue.toLong())
                                scrubbing = false
                            },
                        )
                        Text(
                            "${formatMs(displayPosition.toLong())} / ${formatMs(durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Column {
                        Text(
                            "Volume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = current.volume.coerceIn(0f, 1f),
                            onValueChange = { value ->
                                manager.sendRemoteCommand(PlaybackActions.SET_VOLUME, volume = value)
                            },
                        )
                    }
                }
            }
        }
    }
}
