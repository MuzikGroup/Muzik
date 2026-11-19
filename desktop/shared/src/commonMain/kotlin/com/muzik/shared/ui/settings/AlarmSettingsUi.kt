/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@file:Suppress("FunctionName", "ktlint:standard:no-wildcard-imports")

package com.muzik.shared.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muzik.shared.alarm.MusicAlarmCodec
import com.muzik.shared.alarm.MusicAlarmEntry
import com.muzik.shared.alarm.clockLabel
import com.muzik.shared.db.entities.Playlist
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.alarm_duplicate_time_warning
import com.muzik.shared.generated.resources.alarm_edit
import com.muzik.shared.generated.resources.alarm_new
import com.muzik.shared.generated.resources.alarm_no_playlists
import com.muzik.shared.generated.resources.alarm_not_scheduled
import com.muzik.shared.generated.resources.alarm_next_prefix
import com.muzik.shared.generated.resources.alarm_off
import com.muzik.shared.generated.resources.alarm_playlist
import com.muzik.shared.generated.resources.alarm_playlist_song_count
import com.muzik.shared.generated.resources.alarm_random_disabled
import com.muzik.shared.generated.resources.alarm_random_enabled
import com.muzik.shared.generated.resources.alarm_random_song
import com.muzik.shared.generated.resources.alarm_requires_playlist
import com.muzik.shared.generated.resources.alarm_select_playlist
import com.muzik.shared.generated.resources.alarm_selected
import com.muzik.shared.generated.resources.alarm_time
import com.muzik.shared.generated.resources.cancel
import com.muzik.shared.generated.resources.delete
import com.muzik.shared.generated.resources.save
import com.muzik.shared.ui.components.BasicAlertDialog
import com.muzik.shared.ui.icons.MuzikIcons
import org.jetbrains.compose.resources.stringResource

/** One alarm entry rendered inside the player settings page, mirroring the Android app. */
@Composable
internal fun AlarmRowContent(
    alarm: MusicAlarmEntry,
    playlistTitle: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    alarm.clockLabel() +
                        if (alarm.enabled) {
                            ""
                        } else {
                            "  •  " + stringResource(Res.string.alarm_off)
                        },
                style = MaterialTheme.typography.titleMedium,
            )
            val nextText =
                alarm.nextTriggerAt
                    .takeIf { it > 0L }
                    ?.let { formatAlarmTriggerDateTime(it) }
                    ?.let { stringResource(Res.string.alarm_next_prefix, it) }
                    ?: stringResource(Res.string.alarm_not_scheduled)
            Text(
                text =
                    buildString {
                        append(playlistTitle)
                        append("  •  ")
                        append(
                            stringResource(
                                if (alarm.randomSong) Res.string.alarm_random_enabled else Res.string.alarm_random_disabled,
                            ),
                        )
                        append("\n")
                        append(nextText)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = MuzikIcons.Outlined.Delete,
                contentDescription = stringResource(Res.string.delete),
            )
        }
    }
}

/** Add/edit dialog for a single music alarm, mirroring the Android app's editor. */
@Composable
internal fun AlarmEditorDialog(
    existing: MusicAlarmEntry?,
    allAlarms: List<MusicAlarmEntry>,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSave: (MusicAlarmEntry) -> Unit,
) {
    var hour by remember { mutableIntStateOf(existing?.hour ?: 7) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var playlistId by remember { mutableStateOf(existing?.playlistId.orEmpty()) }
    var randomSong by remember { mutableStateOf(existing?.randomSong ?: false) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    val hasSameTimeAlarm = allAlarms.any { it.id != existing?.id && it.hour == hour && it.minute == minute }
    val selectedPlaylist = playlists.firstOrNull { it.id == playlistId }

    if (showPlaylistPicker) {
        AlarmPlaylistPickerDialog(
            playlists = playlists,
            selectedId = playlistId,
            onSelect = { playlistId = it },
            onDismiss = { showPlaylistPicker = false },
        )
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (existing == null) Res.string.alarm_new else Res.string.alarm_edit,
                        ),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlarmTimeStepper(
                        label = stringResource(Res.string.alarm_time),
                        value = hour,
                        onIncrement = { hour = (hour + 1) % 24 },
                        onDecrement = { hour = (hour + 23) % 24 },
                    )
                    AlarmTimeStepper(
                        label = "",
                        value = minute,
                        onIncrement = { minute = (minute + 1) % 60 },
                        onDecrement = { minute = (minute + 59) % 60 },
                    )
                }

                OutlinedButton(
                    onClick = { showPlaylistPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text =
                            selectedPlaylist?.title
                                ?: stringResource(Res.string.alarm_select_playlist),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }

                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.alarm_no_playlists),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.alarm_random_song),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = randomSong,
                        onCheckedChange = { randomSong = it },
                    )
                }

                if (hasSameTimeAlarm) {
                    Text(
                        text = stringResource(Res.string.alarm_duplicate_time_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Button(
                        onClick = {
                            onSave(
                                (existing ?: MusicAlarmEntry(id = newAlarmId())).copy(
                                    enabled = existing?.enabled ?: true,
                                    hour = hour,
                                    minute = minute,
                                    playlistId = playlistId,
                                    randomSong = randomSong,
                                ),
                            )
                        },
                        enabled = playlistId.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(Res.string.save))
                    }
                }

                if (playlistId.isBlank()) {
                    Text(
                        text = stringResource(Res.string.alarm_requires_playlist),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmTimeStepper(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDecrement, shapes = ButtonDefaults.shapes()) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "%02d".format(value.coerceIn(0, 99)),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 52.dp),
            )
            TextButton(onClick = onIncrement, shapes = ButtonDefaults.shapes()) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun AlarmPlaylistPickerDialog(
    playlists: List<Playlist>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 320.dp, max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.alarm_playlist),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.alarm_no_playlists),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                    ) {
                        items(items = playlists, key = { it.id }) { playlist ->
                            val selected = playlist.id == selectedId
                            Card(
                                onClick = {
                                    onSelect(playlist.id)
                                    onDismiss()
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor =
                                            if (selected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            },
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = playlist.title,
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text =
                                                stringResource(
                                                    Res.string.alarm_playlist_song_count,
                                                    playlist.songCount,
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = MuzikIcons.Filled.Check,
                                            contentDescription = stringResource(Res.string.alarm_selected),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.cancel))
                    }
                }
            }
        }
    }
}

internal fun newAlarmId(): String = MusicAlarmCodec.newId()
