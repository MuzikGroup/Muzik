package com.muzik.shared.ui.mobile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.muzik.shared.db.MusicDatabase
import com.muzik.shared.model.SongItem
import com.muzik.shared.ui.components.ModalBottomSheet
import com.muzik.shared.ui.menu.SongDetailsContent
import com.muzik.shared.ui.playback.PlaybackUiState
import com.muzik.shared.ui.theme.ComponentShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileSongDetailsSheet(
    state: PlaybackUiState,
    database: MusicDatabase,
    onDismiss: () -> Unit,
    song: SongItem? = state.currentSong,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = ComponentShapes.BottomSheetShape) {
        SongDetailsContent(
            song = song,
            database = database,
            playbackResource = state.currentPlaybackResource,
            durationMs = state.durationMs.takeIf { it > 0 && state.currentSong?.id == song?.id },
        )
    }
}
