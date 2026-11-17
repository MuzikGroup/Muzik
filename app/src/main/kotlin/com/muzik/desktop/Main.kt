package com.muzik.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Tray
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.muzik.desktop.library.AudioQuality
import com.muzik.desktop.library.Library
import com.muzik.desktop.library.ThemeMode
import com.muzik.desktop.player.PlayerEngine
import com.muzik.desktop.theme.MuzikTheme
import com.muzik.desktop.ui.App
import com.muzik.desktop.util.BuildConfigVersion

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 780.dp))

    val library = remember { Library() }
    val player = remember {
        PlayerEngine(
            highQualityProvider = {
                library.settings.value.audioQuality == AudioQuality.HIGH.name
            },
            onSongStarted = { song -> library.recordPlay(song) },
        )
    }

    val settings by library.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val playerState by player.state.collectAsState()

    LaunchedEffect(playerState.error) {
        playerState.error?.let { snackbar.showSnackbar(it.substringBefore('\n')) }
    }

    MuzikTheme(themeMode = ThemeMode.valueOf(settings.theme)) {
        val icon = painterResource("icon.png")

        Window(
            onCloseRequest = {
                player.dispose()
                exitApplication()
            },
            state = windowState,
            title = "Muzik",
            icon = icon,
        ) {
            Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
                Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
                    App(library = library, player = player)
                }
            }
        }

        Tray(
            icon = icon,
            tooltip = "Muzik — ${BuildConfigVersion.VERSION}",
            menu = {
                Item(
                    if (playerState.isPlaying) "Pause" else "Play",
                    onClick = { player.playPause() },
                )
                Item("Next", onClick = { player.next() })
                Item("Previous", onClick = { player.previous() })
                Separator()
                Item(
                    "Show Muzik",
                    onClick = { windowState.isMinimized = false },
                )
                Item("Quit Muzik", onClick = {
                    player.dispose()
                    exitApplication()
                })
            },
        )
    }
}
