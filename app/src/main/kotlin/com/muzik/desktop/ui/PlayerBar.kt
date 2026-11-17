package com.muzik.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muzik.desktop.library.AudioQuality
import com.muzik.desktop.library.Library
import com.muzik.desktop.player.PlayerEngine
import com.muzik.desktop.player.RepeatMode

fun formatMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

@Composable
fun PlayerBar(player: PlayerEngine, library: Library, onToggleQueue: () -> Unit) {
    val state by player.state.collectAsState()
    val volume by player.volume.collectAsState()
    val song = state.current

    Surface(shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverArt(song?.thumbnail, Modifier.size(52.dp))
                Column(Modifier.width(220.dp)) {
                    Text(
                        song?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song?.artist ?: "Search for something to get started",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Seek bar
                Column(Modifier.weight(1f)) {
                    Slider(
                        value = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                        onValueChange = { frac -> player.seekTo((frac * state.durationMs).toLong()) },
                        enabled = state.durationMs > 0,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMs(state.positionMs), style = MaterialTheme.typography.labelSmall)
                        Text(formatMs(state.durationMs), style = MaterialTheme.typography.labelSmall)
                    }
                }

                IconButton(onClick = { player.setShuffle(!state.shuffle) }) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(onClick = { player.playPause() }, Modifier.size(44.dp)) {
                    when {
                        state.isLoading -> CircularProgressIndicator(Modifier.size(24.dp))
                        state.isPlaying -> Icon(Icons.Filled.Pause, contentDescription = "Pause")
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                    }
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = {
                    player.setRepeat(
                        when (state.repeat) {
                            RepeatMode.OFF -> RepeatMode.ALL
                            RepeatMode.ALL -> RepeatMode.ONE
                            RepeatMode.ONE -> RepeatMode.OFF
                        }
                    )
                }) {
                    Icon(
                        when (state.repeat) {
                            RepeatMode.ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (state.repeat != RepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Slider(
                    value = volume,
                    onValueChange = { player.setVolume(it) },
                    modifier = Modifier.width(110.dp),
                )
                IconButton(onClick = onToggleQueue) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.error?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun QueueSheet(player: PlayerEngine, onClose: () -> Unit) {
    val state by player.state.collectAsState()
    Surface(
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Queue  (${state.queue.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Text(
                "Radio continues when the queue runs out" + if (state.autoplayRadio) "" else "  (autoplay off)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexedCompat(state.queue, state.index) { i, song, current ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { player.playFrom(state.queue, i) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CoverArt(song.thumbnail, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(song.durationText ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// Small adapter so QueueSheet can mark the current row without re-indexing flows.
private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    items: List<T>,
    currentIndex: Int,
    item: @Composable (Int, T, Boolean) -> Unit,
) {
    items(items.size) { i ->
        item(i, items[i], i == currentIndex)
    }
}

@Composable
fun rememberQualityLabel(library: Library): String {
    val settings by library.settings.collectAsState()
    return when (settings.audioQuality) {
        AudioQuality.HIGH.name -> "High (up to 256 kbps AAC)"
        AudioQuality.LOW.name -> "Low"
        else -> "Normal (128 kbps AAC)"
    }
}
