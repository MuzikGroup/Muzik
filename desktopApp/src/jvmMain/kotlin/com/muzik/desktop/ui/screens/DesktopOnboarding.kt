@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.muzik.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.muzik.desktop.backup.DesktopBackupRestoreManager
import com.muzik.desktop.state.DesktopLocaleManager
import com.muzik.desktop.state.appLanguageChoices
import com.muzik.desktop.utils.pickDesktopFile
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.app_name
import com.muzik.shared.generated.resources.back
import com.muzik.shared.generated.resources.continue_action
import com.muzik.shared.generated.resources.finish
import com.muzik.shared.generated.resources.log_in
import com.muzik.shared.generated.resources.logo
import com.muzik.shared.generated.resources.no
import com.muzik.shared.generated.resources.onboarding_backup_picker_error
import com.muzik.shared.generated.resources.onboarding_backup_picker_title
import com.muzik.shared.generated.resources.onboarding_backup_restore_failed
import com.muzik.shared.generated.resources.onboarding_begin_setup
import com.muzik.shared.generated.resources.onboarding_crash_reports_description
import com.muzik.shared.generated.resources.onboarding_crash_reports_title
import com.muzik.shared.generated.resources.onboarding_finish_description
import com.muzik.shared.generated.resources.onboarding_finish_title
import com.muzik.shared.generated.resources.onboarding_language_lyrics_description
import com.muzik.shared.generated.resources.onboarding_playback_description
import com.muzik.shared.generated.resources.onboarding_sentry_diagnostics
import com.muzik.shared.generated.resources.onboarding_sentry_opt_in
import com.muzik.shared.generated.resources.onboarding_sentry_private
import com.muzik.shared.generated.resources.onboarding_settings_hint
import com.muzik.shared.generated.resources.onboarding_step_of
import com.muzik.shared.generated.resources.onboarding_welcome_privacy_description
import com.muzik.shared.generated.resources.privacy_policy
import com.muzik.shared.generated.resources.restore_from_backup
import com.muzik.shared.generated.resources.settings_localization
import com.muzik.shared.generated.resources.settings_playback
import com.muzik.shared.generated.resources.side_menu_welcome
import com.muzik.shared.generated.resources.yes
import com.muzik.shared.settings.Settings
import com.muzik.shared.ui.components.ContainedLoadingIndicator
import com.muzik.shared.ui.components.GroupedListItem
import com.muzik.shared.ui.icons.MuzikIcons
import com.muzik.shared.ui.onboarding.OnboardingPage
import com.muzik.shared.ui.onboarding.OnboardingPageContent
import com.muzik.shared.ui.onboarding.OnboardingPreferences
import com.muzik.shared.ui.onboarding.OnboardingTransition
import com.muzik.shared.ui.settings.SettingsHostChoice
import com.muzik.shared.ui.settings.SettingsPageId
import com.muzik.shared.ui.settings.SettingsPlatform
import com.muzik.shared.ui.settings.SharedSettingsHost
import com.muzik.shared.ui.utils.verticalScrollWithDrag
import com.muzik.shared.utils.isCrashReportingAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun DesktopOnboarding(onComplete: (sendCrashReports: Boolean, openLogin: Boolean) -> Unit) {
    val pages =
        remember {
            buildList {
                add(OnboardingPage.WELCOME)
                if (isCrashReportingAvailable()) add(OnboardingPage.CRASH_REPORTS)
                add(OnboardingPage.LANGUAGE_LYRICS)
                add(OnboardingPage.PLAYBACK)
                add(OnboardingPage.FINISH)
            }
        }
    val languages = appLanguageChoices()
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    var sendCrashReports by rememberSaveable { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val restorePickerTitle = stringResource(Res.string.onboarding_backup_picker_title)

    fun nextPage() {
        error = null
        if (pageIndex < pages.lastIndex) {
            pageIndex++
        } else {
            onComplete(sendCrashReports, false)
        }
    }

    fun restoreBackup() {
        if (isWorking) return
        isWorking = true
        scope.launch {
            try {
                error = null
                val selected =
                    runCatching {
                        pickDesktopFile(restorePickerTitle, listOf("zip", "backup"))
                    }.getOrElse {
                        if (it is CancellationException) throw it
                        error = getString(Res.string.onboarding_backup_picker_error, it.message ?: "")
                        return@launch
                    } ?: return@launch

                withContext(Dispatchers.IO) {
                    DesktopBackupRestoreManager.stageRestoreAndRestart(selected) {
                        Settings.shared.desktopOnboardingComplete = true
                        if (isCrashReportingAvailable()) {
                            Settings.shared.desktopPendingOnboardingSentryConsent = sendCrashReports.toString()
                        }
                    }
                }.onFailure {
                    Settings.shared.desktopOnboardingComplete = false
                    Settings.shared.desktopPendingOnboardingSentryConsent = ""
                    error = getString(Res.string.onboarding_backup_restore_failed, it.message ?: "")
                }
            } finally {
                isWorking = false
            }
        }
    }

    @Composable
    fun pageContent(index: Int): OnboardingPageContent {
        val page = pages[index]
        return OnboardingPageContent(
            page = page,
            title =
                when (page) {
                    OnboardingPage.WELCOME -> stringResource(Res.string.side_menu_welcome)
                    OnboardingPage.CRASH_REPORTS -> stringResource(Res.string.onboarding_crash_reports_title)
                    OnboardingPage.LANGUAGE_LYRICS -> stringResource(Res.string.settings_localization)
                    OnboardingPage.PLAYBACK -> stringResource(Res.string.settings_playback)
                    OnboardingPage.FINISH -> stringResource(Res.string.onboarding_finish_title)
                    else -> error("Unsupported desktop onboarding page")
                },
            description =
                when (page) {
                    OnboardingPage.WELCOME -> {
                        stringResource(Res.string.onboarding_welcome_privacy_description)
                    }

                    OnboardingPage.CRASH_REPORTS -> {
                        stringResource(Res.string.onboarding_crash_reports_description)
                    }

                    OnboardingPage.LANGUAGE_LYRICS -> {
                        stringResource(Res.string.onboarding_language_lyrics_description)
                    }

                    OnboardingPage.PLAYBACK -> {
                        stringResource(Res.string.onboarding_playback_description)
                    }

                    OnboardingPage.FINISH -> {
                        stringResource(Res.string.onboarding_finish_description)
                    }
                },
            icon =
                when (page) {
                    OnboardingPage.WELCOME -> MuzikIcons.Outlined.MusicNote
                    OnboardingPage.CRASH_REPORTS -> MuzikIcons.Outlined.Security
                    OnboardingPage.LANGUAGE_LYRICS -> MuzikIcons.Outlined.Language
                    OnboardingPage.PLAYBACK -> MuzikIcons.Outlined.PlayArrow
                    OnboardingPage.FINISH -> MuzikIcons.Filled.Check
                },
            step = index + 1,
            stepCount = pages.size,
            primaryLabel =
                when (page) {
                    OnboardingPage.WELCOME -> stringResource(Res.string.onboarding_begin_setup)
                    OnboardingPage.CRASH_REPORTS -> stringResource(Res.string.yes)
                    OnboardingPage.LANGUAGE_LYRICS, OnboardingPage.PLAYBACK -> stringResource(Res.string.continue_action)
                    OnboardingPage.FINISH -> stringResource(Res.string.finish)
                },
            secondaryLabel = if (page == OnboardingPage.CRASH_REPORTS) stringResource(Res.string.no) else null,
            isWorking = isWorking,
            error = error.takeIf { index == pageIndex },
            backLabel = stringResource(Res.string.back),
            onPrimary = {
                if (page == OnboardingPage.CRASH_REPORTS) sendCrashReports = true
                nextPage()
            },
            onSecondary =
                if (page == OnboardingPage.CRASH_REPORTS) {
                    {
                        sendCrashReports = false
                        nextPage()
                    }
                } else {
                    null
                },
            onBack =
                if (index > 0) {
                    {
                        error = null
                        pageIndex--
                    }
                } else {
                    null
                },
        )
    }
    val content = pageContent(pageIndex)
    val previousContent = if (pageIndex > 0) pageContent(pageIndex - 1) else null

    DesktopOnboardingCard(content, previousContent) { targetPage ->
        if (targetPage == OnboardingPage.LANGUAGE_LYRICS || targetPage == OnboardingPage.PLAYBACK) {
            OnboardingPreferences(
                page = targetPage,
                host =
                    SharedSettingsHost(
                        platform = SettingsPlatform.DESKTOP,
                        onClose = {},
                        availablePages = setOf(SettingsPageId.LYRICS_AI_TRANSLATION, SettingsPageId.LYRICS_ROMANIZATION),
                        hostChoices = mapOf(SettingsHostChoice.APP_LANGUAGE to languages),
                        onHostChoiceSelected = { _, selected ->
                            Settings.shared.appLanguage = selected
                            DesktopLocaleManager.apply(selected)
                        },
                    ),
            )
        }
        if (targetPage == OnboardingPage.FINISH) {
            FinishActions(
                onLogin = { onComplete(sendCrashReports, true) },
                onRestore = ::restoreBackup,
                enabled = !isWorking,
            )
        }
        if (isWorking) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ContainedLoadingIndicator()
            }
        }
    }
}

@Composable
private fun DesktopOnboardingCard(
    content: OnboardingPageContent,
    previousContent: OnboardingPageContent?,
    featureContent: @Composable (OnboardingPage) -> Unit,
) {
    OnboardingTransition(content, previousContent) { page ->
        DesktopOnboardingPage(page, featureContent)
    }
}

@Composable
private fun DesktopOnboardingPage(
    content: OnboardingPageContent,
    featureContent: @Composable (OnboardingPage) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val stepDescription = stringResource(Res.string.onboarding_step_of, content.step, content.stepCount)
    Box(
        Modifier.fillMaxSize().background(colors.surfaceContainer).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier =
                Modifier.widthIn(max = 720.dp).heightIn(max = 600.dp).fillMaxWidth().fillMaxHeight().semantics {
                    paneTitle = content.title
                    stateDescription = stepDescription
                },
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(Res.drawable.logo),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.onSurface),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    StepIndicator(step = content.step, stepCount = content.stepCount)
                }
                HorizontalDivider(color = colors.outlineVariant)
                Box(Modifier.weight(1f)) {
                    val page = content
                    Column(Modifier.fillMaxWidth().verticalScrollWithDrag(rememberScrollState()).padding(32.dp)) {
                        Icon(page.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
                        Text(
                            page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
                        )
                        Text(
                            page.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (page.page == OnboardingPage.CRASH_REPORTS) SentryDetails()
                        featureContent(page.page)
                        page.error?.let {
                            Text(
                                it,
                                color = colors.error,
                                modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (content.page == OnboardingPage.WELCOME || content.page == OnboardingPage.CRASH_REPORTS) {
                        val uriHandler = LocalUriHandler.current
                        TextButton(
                            onClick = { runCatching { uriHandler.openUri("https://muzik.cc/privacy") } },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(Res.string.privacy_policy))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    content.onBack?.let { back ->
                        TextButton(
                            onClick = back,
                            enabled = !content.isWorking,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(content.backLabel) }
                    }
                    content.secondaryLabel?.let { secondary ->
                        OutlinedButton(
                            onClick = content.onSecondary ?: {},
                            enabled = !content.isWorking,
                            shapes = ButtonDefaults.shapes(),
                        ) { Text(secondary) }
                    }
                    Button(
                        onClick = content.onPrimary,
                        enabled = !content.isWorking,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text(content.primaryLabel) }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    step: Int,
    stepCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(stepCount) { index ->
            Box(
                Modifier
                    .size(width = if (index + 1 == step) 20.dp else 8.dp, height = 8.dp)
                    .background(
                        if (index + 1 <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun SentryDetails() {
    val details =
        listOf(
            MuzikIcons.Outlined.Tune to stringResource(Res.string.onboarding_sentry_opt_in),
            MuzikIcons.Outlined.Info to stringResource(Res.string.onboarding_sentry_diagnostics),
            MuzikIcons.Rounded.Lock to stringResource(Res.string.onboarding_sentry_private),
        )
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        details.forEachIndexed { index, (icon, label) ->
            GroupedListItem(index = index, count = details.size) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun FinishActions(
    onLogin: () -> Unit,
    onRestore: () -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = onLogin,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(MuzikIcons.AutoMirrored.Filled.Login, contentDescription = null)
            Text(stringResource(Res.string.log_in), modifier = Modifier.padding(start = 10.dp))
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(MuzikIcons.Outlined.SettingsBackupRestore, contentDescription = null)
            Text(stringResource(Res.string.restore_from_backup), modifier = Modifier.padding(start = 10.dp))
        }
        Text(
            stringResource(Res.string.onboarding_settings_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
