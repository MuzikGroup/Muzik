/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzikgroup.music.link

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.muzikgroup.music.LocalListenTogetherManager
import com.muzikgroup.music.R
import com.muzikgroup.music.LocalMuzikLinkManager
import com.muzikgroup.music.constants.AppBarHeight
import com.muzikgroup.music.listentogether.LinkDeviceInfo
import com.muzikgroup.music.listentogether.LinkDeviceTypes
import com.muzikgroup.music.listentogether.LinkStatePayload
import com.muzikgroup.music.listentogether.PlaybackActions

private fun deviceLabel(deviceType: String): String =
    when (deviceType) {
        LinkDeviceTypes.ANDROID -> "Android device"
        LinkDeviceTypes.DESKTOP -> "Computer"
        LinkDeviceTypes.IOS -> "iOS device"
        else -> "Device"
    }

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun MuzikLinkScreen(navController: NavController) {
    val manager = LocalMuzikLinkManager.current ?: return
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val windowInsets = com.muzikgroup.music.LocalPlayerAwareWindowInsets.current

    val connectionState by manager.connectionState.collectAsStateWithLifecycle()
    val session by manager.linkSession.collectAsStateWithLifecycle()
    val remoteState by manager.remoteState.collectAsStateWithLifecycle()
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoom = listenTogetherManager?.roomState?.collectAsStateWithLifecycle()?.value

    var joinCode by remember { mutableStateOf("") }
    var joinInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        manager.uiEvents.collect { event ->
            when (event) {
                is MuzikLinkUiEvent.Error -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                    joinInProgress = false
                }
                is MuzikLinkUiEvent.Info -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    joinInProgress = false
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = windowInsets.asPaddingValues().calculateTopPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = windowInsets.asPaddingValues().calculateBottomPadding() + 16.dp + AppBarHeight,
                ),
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

        when (val currentSession = session) {
            null -> {
                if (connectionState == LinkConnectionState.CONNECTING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.padding(16.dp),
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
                        modifier = Modifier.padding(16.dp),
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
                            onClick = {
                                if (joinCode.isBlank()) {
                                    Toast.makeText(context, "Enter a pairing code first", Toast.LENGTH_SHORT).show()
                                } else {
                                    joinInProgress = true
                                    manager.joinLink(joinCode)
                                }
                            },
                            enabled = !joinInProgress && connectionState != LinkConnectionState.CONNECTING,
                        ) {
                            Text("Pair this device")
                        }
                    }
                }
            }

            else -> {
                val isPlayer = currentSession.playerDeviceId == currentSession.deviceId
                val dormant = listenTogetherRoom != null

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(currentSession.pairingCode))
                            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(painterResource(R.drawable.content_copy), contentDescription = "Copy pairing code")
                        }
                    }
                }

                if (dormant) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Paused while Listen Together is active. Leave the room to use Muzik Link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Devices", style = MaterialTheme.typography.titleMedium)
                        currentSession.devices.forEach { device ->
                            DeviceRow(
                                device = device,
                                isSelf = device.deviceId == currentSession.deviceId,
                                canHandoff = !dormant,
                                onHandoff = { manager.handoffTo(device.deviceId) },
                            )
                        }
                    }
                }

                if (!isPlayer && !dormant) {
                    RemoteControlCard(remoteState, manager)
                }

                if (isPlayer) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "This device is the active player. Control it from your other paired devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }

                OutlinedButton(
                    onClick = { manager.leaveLink() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(painterResource(R.drawable.logout), contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Leave link")
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: LinkDeviceInfo,
    isSelf: Boolean,
    canHandoff: Boolean,
    onHandoff: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                device.deviceName + if (isSelf) " (this device)" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (device.isPlayer) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                if (device.isPlayer) "Active player" else deviceLabel(device.deviceType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!device.isPlayer && !isSelf && canHandoff) {
            FilledTonalButton(onClick = onHandoff) {
                Text("Play here")
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
            modifier = Modifier.padding(16.dp),
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
                            Icon(painterResource(R.drawable.skip_previous), contentDescription = "Previous")
                        }
                        FilledTonalIconButton(onClick = {
                            if (current.isPlaying) {
                                manager.sendRemoteCommand(PlaybackActions.PAUSE, position = current.position)
                            } else {
                                manager.sendRemoteCommand(PlaybackActions.PLAY, position = current.position)
                            }
                        }) {
                            Icon(
                                painterResource(if (current.isPlaying) R.drawable.pause else R.drawable.play),
                                contentDescription = if (current.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        FilledTonalIconButton(onClick = { manager.sendRemoteCommand(PlaybackActions.SKIP_NEXT) }) {
                            Icon(painterResource(R.drawable.skip_next), contentDescription = "Next")
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
