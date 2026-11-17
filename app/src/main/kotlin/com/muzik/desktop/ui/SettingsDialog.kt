@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.muzik.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muzik.desktop.library.AudioQuality
import com.muzik.desktop.library.Library
import com.muzik.desktop.library.ThemeMode
import com.muzik.desktop.player.PlayerEngine
import com.muzik.desktop.util.BuildConfigVersion

@Composable
fun SettingsDialog(library: Library, player: PlayerEngine, onDismiss: () -> Unit) {
    val settings by library.settings.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            Text("Appearance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "System" to ThemeMode.SYSTEM.name,
                    "Light" to ThemeMode.LIGHT.name,
                    "Dark" to ThemeMode.DARK.name,
                ).forEach { (label, value) ->
                    if (settings.theme == value) Button(onClick = { library.updateSettings { it.copy(theme = value) } }) { Text(label) }
                    else OutlinedButton(onClick = { library.updateSettings { it.copy(theme = value) } }) { Text(label) }
                }
            }

            Text("Audio quality", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Low" to AudioQuality.LOW.name,
                    "Normal" to AudioQuality.NORMAL.name,
                    "High" to AudioQuality.HIGH.name,
                ).forEach { (label, value) ->
                    if (settings.audioQuality == value) Button(onClick = { library.updateSettings { it.copy(audioQuality = value) } }) { Text(label) }
                    else OutlinedButton(onClick = { library.updateSettings { it.copy(audioQuality = value) } }) { Text(label) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates at startup", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Keeps Muzik fresh by checking GitHub releases.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.checkUpdates,
                    onCheckedChange = { checked -> library.updateSettings { s -> s.copy(checkUpdates = checked) } },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Autoplay radio", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Keep the music going when the queue runs out.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = player.state.collectAsState().value.autoplayRadio,
                    onCheckedChange = { player.setAutoplayRadio(it) },
                )
            }

            TextButton(
                onClick = { library.clearHistory() },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Clear listening history", color = MaterialTheme.colorScheme.error) }

            Text(
                "Muzik Desktop ${BuildConfigVersion.VERSION} — a free, open-source YouTube Music client by MuzikGroup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}
