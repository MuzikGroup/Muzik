/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.muzik.desktop.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.muzik.desktop.state.app.AppState
import com.muzik.desktop.state.app.UiState
import com.muzik.desktop.utils.AppLogger
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.support_button_label
import com.muzik.shared.generated.resources.support_dialog_action
import com.muzik.shared.generated.resources.support_dialog_dismiss
import com.muzik.shared.generated.resources.support_dialog_text
import com.muzik.shared.generated.resources.support_dialog_title
import com.muzik.shared.generated.resources.support_thanks_share
import com.muzik.shared.generated.resources.support_thanks_text
import com.muzik.shared.generated.resources.support_thanks_title
import com.muzik.shared.settings.Settings
import com.muzik.shared.ui.icons.MuzikIconsBrand
import com.muzik.shared.utils.tryOpenUrl
import org.jetbrains.compose.resources.stringResource
import java.time.Duration
import java.time.Instant

private const val SUPPORT_URL = "https://buymesoda.com/lllcuDCr25N1a1peiKOCl22Oaf13"
private const val REPO_URL = "https://github.com/MuzikGroup/Muzik"
private const val THANKS_GRACE_DAYS = 7L

/**
 * The coffee-cup button that lives in the top bar. Clicking it takes the user
 * straight to Buy Me a Soda, with a short ask before the redirect and a
 * thank-you + share prompt once they come back after supporting.
 */
@Composable
fun SupportButton(colorScheme: ColorScheme) {
    val supportLabel = stringResource(Res.string.support_button_label)
    var showSupportDialog by rememberSaveable { mutableStateOf(false) }
    var showThanksDialog by rememberSaveable { mutableStateOf(false) }

    fun shouldThank(): Boolean {
        val openedAt = Settings.shared.supportPageOpenedAt
        if (openedAt <= 0L) return false
        if (Settings.shared.supportThanksShownFor == openedAt) return false
        val age = Duration.between(Instant.ofEpochMilli(openedAt), Instant.now())
        return !age.isNegative && age.toDays() < THANKS_GRACE_DAYS
    }

    // When the window regains focus (the user returning from the support site),
    // check whether we owe them a thank-you.
    LaunchedEffect(Unit) {
        snapshotFlow { UiState.isWindowFocused }.collect { focused ->
            if (focused && shouldThank()) {
                showThanksDialog = true
                showSupportDialog = false
            }
        }
    }

    IconButton(
        onClick = { showSupportDialog = true },
        modifier =
            Modifier
                .size(40.dp)
                .semantics { contentDescription = supportLabel },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            MuzikIconsBrand.BuyMeACoffee,
            contentDescription = supportLabel,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }

    if (showSupportDialog) {
        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text(stringResource(Res.string.support_dialog_title)) },
            text = { Text(stringResource(Res.string.support_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSupportDialog = false
                        Settings.shared.supportPageOpenedAt = System.currentTimeMillis()
                        tryOpenUrl(SUPPORT_URL) { opened ->
                            if (!opened) AppLogger.warning("Support", "Unable to open $SUPPORT_URL")
                        }
                    },
                ) { Text(stringResource(Res.string.support_dialog_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showSupportDialog = false }) {
                    Text(stringResource(Res.string.support_dialog_dismiss))
                }
            },
        )
    }

    if (showThanksDialog) {
        val openedAt = Settings.shared.supportPageOpenedAt
        AlertDialog(
            onDismissRequest = {
                if (openedAt > 0L) Settings.shared.supportThanksShownFor = openedAt
                showThanksDialog = false
            },
            title = { Text(stringResource(Res.string.support_thanks_title)) },
            text = {
                Text(
                    stringResource(Res.string.support_thanks_text),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (openedAt > 0L) Settings.shared.supportThanksShownFor = openedAt
                        showThanksDialog = false
                        AppState.shareLink(REPO_URL, "Muzik")
                    },
                ) { Text(stringResource(Res.string.support_thanks_share)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (openedAt > 0L) Settings.shared.supportThanksShownFor = openedAt
                    showThanksDialog = false
                }) { Text(stringResource(Res.string.support_dialog_dismiss)) }
            },
        )
    }
}
