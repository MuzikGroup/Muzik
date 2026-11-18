/*
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzikgroup.music.ui.component.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.muzikgroup.music.BuildConfig
import com.muzikgroup.music.R
import com.muzikgroup.music.constants.SupportPageOpenedAtKey
import com.muzikgroup.music.constants.SupportThanksShownForKey
import com.muzikgroup.music.utils.rememberPreference
import java.time.Duration
import java.time.Instant

private const val SUPPORT_URL = "https://buymesoda.com/lllcuDCr25N1a1peiKOCl22Oaf13"
private const val THANKS_GRACE_DAYS = 7L

/**
 * The coffee-cup button that sits beside the History icon in the top bar.
 *
 * Pressing it takes the user straight to Buy Me a Soda while a small message
 * pops up asking for their support. When they come back after visiting the
 * page, a thank-you dialog appears and asks them to share Muzik with family
 * and friends.
 */
@Composable
fun SupportCoffeeButton(
    onAskForSupport: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var (supportPageOpenedAt, onSupportPageOpenedAtChange) =
        rememberPreference(SupportPageOpenedAtKey, 0L)
    var (supportThanksShownFor, onSupportThanksShownForChange) =
        rememberPreference(SupportThanksShownForKey, 0L)
    var showThanksDialog by remember { mutableStateOf(false) }

    LaunchedEffect(supportPageOpenedAt) {
        if (supportPageOpenedAt > 0L && supportThanksShownFor != supportPageOpenedAt) {
            val age = Duration.between(Instant.ofEpochMilli(supportPageOpenedAt), Instant.now())
            if (!age.isNegative && age.toDays() < THANKS_GRACE_DAYS) {
                showThanksDialog = true
            }
        }
    }

    fun markThanksShown() {
        onSupportThanksShownForChange(supportPageOpenedAt)
    }

    IconButton(onClick = {
        onSupportPageOpenedAtChange(System.currentTimeMillis())
        try {
            uriHandler.openUri(SUPPORT_URL)
        } catch (_: Exception) {
            android.widget.Toast
                .makeText(context, SUPPORT_URL, android.widget.Toast.LENGTH_LONG)
                .show()
        }
        onAskForSupport()
    }) {
        Icon(
            painter = painterResource(R.drawable.ic_coffee),
            contentDescription = stringResource(R.string.support_muzik),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showThanksDialog) {
        AlertDialog(
            onDismissRequest = { markThanksShown(); showThanksDialog = false },
            title = { Text(stringResource(R.string.support_thanks_title)) },
            text = { Text(stringResource(R.string.support_thanks_text)) },
            confirmButton = {
                TextButton(onClick = {
                    markThanksShown()
                    showThanksDialog = false
                    try {
                        val shareIntent =
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "I am listening with Muzik — a free, open-source music player. Try it: https://github.com/MuzikGroup/Muzik (v${BuildConfig.VERSION_NAME})",
                                )
                            }
                        context.startActivity(
                            android.content.Intent.createChooser(shareIntent, context.getString(R.string.support_thanks_share)),
                        )
                    } catch (_: Exception) {
                    }
                }) { Text(stringResource(R.string.support_thanks_share)) }
            },
            dismissButton = {
                TextButton(onClick = { markThanksShown(); showThanksDialog = false }) {
                    Text(stringResource(R.string.support_maybe_later))
                }
            },
        )
    }
}
