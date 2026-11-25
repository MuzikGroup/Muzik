/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.desktop.state.playback

import com.muzik.desktop.listentogether.DesktopListenTogetherPlayback
import com.muzik.desktop.state.app.AppState
import com.muzik.desktop.utils.AppLogger
import com.muzik.shared.deviceconnect.buildLocalDeviceInfo
import com.muzik.shared.listentogether.ListenTogetherLogLevel
import com.muzik.shared.listentogether.ListenTogetherLogger
import com.muzik.shared.listentogether.ListenTogetherTrackResolver
import com.muzik.shared.listentogether.MuzikLinkManager
import com.muzik.shared.listentogether.network.LinkDeviceTypes
import com.muzik.shared.listentogether.toSongItem
import com.muzik.shared.network.HttpFailureLog
import com.muzik.shared.settings.Settings
import com.muzik.shared.state.GlobalYouTubeRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App-wide singleton wiring for Muzik Link. The manager shares the
 * Listen Together playback port so remote control and handoff drive the
 * player through the exact same path as Listen Together guests do.
 */
object MuzikLinkState {
    private const val TAG = "MuzikLink"
    private const val MAX_FRAME_SIZE_BYTES = 256 * 1024

    private val httpClient =
        HttpClient(CIO) {
            install(HttpFailureLog)
            install(WebSockets) {
                maxFrameSize = MAX_FRAME_SIZE_BYTES
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000L
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = 60_000L
            }
        }

    val muzikLinkManager =
        MuzikLinkManager(
            callerScope = AppState.scope,
            httpClient = httpClient,
            playback = DesktopListenTogetherPlayback(),
            resolver =
                ListenTogetherTrackResolver { track ->
                    GlobalYouTubeRepository.instance.getSongMetadata(track.id) ?: track.toSongItem()
                },
            deviceNameProvider = { Settings.shared.muzikLinkDeviceName.ifBlank { buildLocalDeviceInfo().deviceName } },
            deviceTypeProvider = { LinkDeviceTypes.DESKTOP },
            monotonicClockMs = { System.nanoTime() / 1_000_000L },
            wallClockMs = System::currentTimeMillis,
            logger =
                ListenTogetherLogger { level, message ->
                    when (level) {
                        ListenTogetherLogLevel.INFO -> AppLogger.info(TAG, message)
                        ListenTogetherLogLevel.WARNING -> AppLogger.warning(TAG, message)
                        ListenTogetherLogLevel.ERROR -> AppLogger.error(TAG, message)
                        ListenTogetherLogLevel.DEBUG -> AppLogger.debug(TAG, message)
                    }
                },
            codecDispatcher = Dispatchers.Default,
            isListenTogetherActive = { ListenTogetherState.listenTogetherManager.hasActiveOrPendingRoom },
        )

    fun initialize() {
        // The manager auto-connects when a saved pairing code exists.
    }

    fun close() {
        AppState.scope.launch {
            runCatching { muzikLinkManager.close() }
        }
        httpClient.close()
    }
}
