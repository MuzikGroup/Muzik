package com.muzik.shared.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.muzik.shared.auth.YouTubeSessionState
import com.muzik.shared.auth.YouTubeSyncState
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.try_again
import com.muzik.shared.generated.resources.youtube_sync_failed
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun YouTubeSyncSnackbar(snackbarHostState: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(snackbarHostState) {
        YouTubeSyncState.failures.map { it.values.firstOrNull() }.distinctUntilChanged().collectLatest { current ->
            val failure = current ?: return@collectLatest
            if (YouTubeSessionState.showExpiredSessionNotice) return@collectLatest
            val result =
                snackbarHostState.showSnackbar(
                    message = getString(Res.string.youtube_sync_failed),
                    actionLabel = getString(Res.string.try_again),
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                scope.launch {
                    YouTubeSyncState.succeeded(failure.operation)
                    failure.retry()
                }
            }
        }
    }
}
