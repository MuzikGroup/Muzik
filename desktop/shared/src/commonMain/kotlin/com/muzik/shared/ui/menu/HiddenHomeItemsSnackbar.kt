package com.muzik.shared.ui.menu

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.hidden_from_home
import com.muzik.shared.generated.resources.undo
import com.muzik.shared.settings.HiddenHomeItems
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.getString

@Composable
fun HiddenHomeItemsSnackbar(snackbarHostState: SnackbarHostState) {
    LaunchedEffect(snackbarHostState) {
        HiddenHomeItems.hidden.collectLatest { item ->
            if (snackbarHostState.currentSnackbarData?.visuals !is ContentUndoHintVisuals) {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val result =
                snackbarHostState.showSnackbar(
                    message = getString(Res.string.hidden_from_home, item.title),
                    actionLabel = getString(Res.string.undo),
                    duration = SnackbarDuration.Short,
                )
            if (result == SnackbarResult.ActionPerformed) HiddenHomeItems.restore(item.key)
        }
    }
}
