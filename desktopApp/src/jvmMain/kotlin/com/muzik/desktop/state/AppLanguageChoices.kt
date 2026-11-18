package com.muzik.desktop.state

import androidx.compose.runtime.Composable
import com.muzik.shared.generated.resources.Res
import com.muzik.shared.generated.resources.app_language_system
import com.muzik.shared.ui.settings.SettingsChoice
import org.jetbrains.compose.resources.stringResource

@Composable
fun appLanguageChoices(): List<SettingsChoice> =
    DesktopLocaleManager.languages().map { language ->
        SettingsChoice(
            language.tag,
            when {
                language.isSystem -> stringResource(Res.string.app_language_system)
                else -> requireNotNull(language.displayName)
            },
        )
    }
