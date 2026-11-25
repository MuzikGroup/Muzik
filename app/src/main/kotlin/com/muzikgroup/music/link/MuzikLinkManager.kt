/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzikgroup.music.link

import android.content.Context
import androidx.media3.common.Player
import com.muzikgroup.innertube.YouTube
import com.muzikgroup.innertube.models.WatchEndpoint
import com.muzikgroup.music.constants.MuzikLinkDeviceNameKey
import com.muzikgroup.music.constants.MuzikLinkPairingCodeKey
import com.muzikgroup.music.extensions.currentMetadata
import com.muzikgroup.music.extensions.metadata
import com.muzikgroup.music.extensions.toMediaItem
import com.muzikgroup.music.listentogether.LinkCommandPayload
import com.muzikgroup.music.listentogether.LinkDeviceInfo
import com.muzikgroup.music.listentogether.LinkSessionPayload
import com.muzikgroup.music.listentogether.LinkStatePayload
import com.muzikgroup.music.listentogether.ListenTogetherManager
import com.muzikgroup.music.listentogether.PlaybackActions
import com.muzikgroup.music.listentogether.TrackInfo
import com.muzikgroup.music.models.toMediaMetadata
import com.muzikgroup.music.playback.PlayerConnection
import com.muzikgroup.music.playback.queues.YouTubeQueue
import com.muzikgroup.music.utils.dataStore
import com.muzikgroup.music.utils.getArtistSeparator
import com.muzikgroup.music.utils.joinToArtistString
import com.muzikgroup.music.utils.safeDataStoreEdit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** User-visible Muzik Link events for toasts/snackbars in the UI. */
sealed class MuzikLinkUiEvent {
    data class Error(val code: String, val message: String) : MuzikLinkUiEvent()
    data class Info(val message: String) : MuzikLinkUiEvent()
}

/**
 * Bridges the Muzik Link websocket client with the local player:
 *  - as the active player, publishes [LinkStatePayload] and applies incoming
 *    [LinkCommandPayload]s;
 *  - as a controller, exposes the player's last known state for remote
 *    control and hands playback off to other devices.
 *
 * The manager goes dormant while a Listen Together room is joined so the two
 * features never fight over the same player.
 */
@Singleton
class MuzikLinkManager
    @Inject
    constructor(
        private val client: MuzikLinkClient,
        @ApplicationContext private val context: Context,
        private val listenTogetherManager: ListenTogetherManager,
    ) {
        companion object {
            private const val TAG = "MuzikLinkManager"
            private const val PUBLISH_HEARTBEAT_MS = 2_000L
            private const val VOLUME_PUBLISH_THRESHOLD = 0.01f
            private const val HANDOFF_READY_TIMEOUT_MS = 5_000L
        }

        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        private val _linkSession = MutableStateFlow<LinkSessionPayload?>(null)
        val linkSession: StateFlow<LinkSessionPayload?> = _linkSession

        /** The player device's last published state, as seen by controllers. */
        private val _remoteState = MutableStateFlow<LinkStatePayload?>(null)
        val remoteState: StateFlow<LinkStatePayload?> = _remoteState

        private val _connectionState = MutableStateFlow(LinkConnectionState.DISCONNECTED)
        val connectionState: StateFlow<LinkConnectionState> = _connectionState

        private val _uiEvents = MutableSharedFlow<MuzikLinkUiEvent>(extraBufferCapacity = 32)
        val uiEvents: SharedFlow<MuzikLinkUiEvent> = _uiEvents

        /** True when this device is the active player of the current link. */
        val isPlayer: Boolean
            get() {
                val session = _linkSession.value ?: return false
                return session.playerDeviceId == session.deviceId
            }

        /** True when this device has joined a link at all. */
        val isInLink: Boolean
            get() = _linkSession.value != null

        private var playerConnection: PlayerConnection? = null
        private var playerListener: Player.Listener? = null
        private var heartbeatJob: Job? = null
        private var volumeJob: Job? = null
        private var eventCollectorJob: Job? = null

        @Volatile
        private var listenTogetherActive = false

        @Volatile
        private var lastPublishedVolume = -1f

        /** Positions applied remotely right now - suppresses echo publishing. */
        @Volatile
        private var suppressPublishUntilMs = 0L

        private var initialized = false

        fun initialize() {
            if (initialized) return
            initialized = true

            eventCollectorJob?.cancel()
            eventCollectorJob =
                scope.launch {
                    launch {
                        client.events.collect { event -> handleEvent(event) }
                    }
                    launch {
                        client.connectionState.collect { state ->
                            _connectionState.value = state
                        }
                    }
                    launch {
                        // Go dormant whenever a Listen Together room is active.
                        listenTogetherManager.roomState.collectLatest { room ->
                            val wasActive = listenTogetherActive
                            listenTogetherActive = room != null
                            if (listenTogetherActive && wasActive != listenTogetherActive) {
                                Timber.tag(TAG).i("Listen Together active - Muzik Link goes dormant")
                            }
                        }
                    }
                }

            // Restore a previous pairing automatically.
            scope.launch {
                val savedCode = context.dataStore.data.first()[MuzikLinkPairingCodeKey]
                if (!savedCode.isNullOrBlank()) {
                    client.autoPairCode = savedCode
                    client.connect()
                }
            }
        }

        private suspend fun deviceName(): String {
            val custom = context.dataStore.data.first()[MuzikLinkDeviceNameKey]
            return custom?.takeIf { it.isNotBlank() } ?: client.linkDeviceName(context)
        }

        private fun handleEvent(event: MuzikLinkEvent) {
            when (event) {
                is MuzikLinkEvent.Registered -> onSession(event.session, created = true)
                is MuzikLinkEvent.Paired -> onSession(event.session, created = false)
                is MuzikLinkEvent.DeviceJoined -> {
                    val current = _linkSession.value ?: return
                    _linkSession.value =
                        current.copy(
                            devices =
                                current.devices
                                    .filterNot { it.deviceId == event.device.deviceId } + event.device,
                        )
                    emitUi(MuzikLinkUiEvent.Info("${event.device.deviceName} joined the link"))
                }
                is MuzikLinkEvent.DeviceLeft -> {
                    val current = _linkSession.value ?: return
                    _linkSession.value =
                        current.copy(
                            devices = current.devices.filterNot { it.deviceId == event.deviceId },
                            playerDeviceId =
                                if (event.wasPlayer) "" else current.playerDeviceId,
                        )
                    if (event.wasPlayer) {
                        _remoteState.value = null
                    }
                    emitUi(MuzikLinkUiEvent.Info("A device left the link"))
                }
                is MuzikLinkEvent.PlayerChanged -> {
                    val current = _linkSession.value ?: return
                    _linkSession.value = current.copy(playerDeviceId = event.newPlayerId)
                    if (event.newPlayerId == current.deviceId) {
                        // We are the new player; remote state becomes ours to own.
                        startPlayerRole()
                    } else {
                        stopPlayerRole()
                    }
                }
                is MuzikLinkEvent.StateUpdated -> _remoteState.value = event.state
                is MuzikLinkEvent.HandoffStart -> onHandoffStart(event)
                is MuzikLinkEvent.Stop -> onLinkStop(event)
                is MuzikLinkEvent.Command -> onCommand(event.command)
                is MuzikLinkEvent.ServerError -> onServerError(event.code, event.message)
                MuzikLinkEvent.Connected, MuzikLinkEvent.Disconnected -> Unit
            }
        }

        private fun onSession(session: LinkSessionPayload, created: Boolean) {
            _linkSession.value = session
            if (!isPlayer) {
                session.state?.let { _remoteState.value = it }
            }
            scope.launch {
                savePairingCode(session.pairingCode)
            }
            if (session.playerDeviceId == session.deviceId) {
                startPlayerRole()
            } else {
                stopPlayerRole()
            }
            emitUi(
                MuzikLinkUiEvent.Info(
                    if (created) "Link created - pairing code ${session.pairingCode}" else "Paired with link",
                ),
            )
        }

        private suspend fun savePairingCode(code: String) {
            context.safeDataStoreEdit { preferences ->
                preferences[MuzikLinkPairingCodeKey] = code
            }
        }

        private suspend fun clearPairingCode() {
            context.safeDataStoreEdit { preferences ->
                preferences.remove(MuzikLinkPairingCodeKey)
            }
        }

        private fun onServerError(code: String, message: String) {
            when (code) {
                "link_not_found" -> {
                    // Saved code is stale - drop it and surface the error.
                    client.autoPairCode = null
                    scope.launch { clearPairingCode() }
                    _linkSession.value = null
                    emitUi(MuzikLinkUiEvent.Error(code, message))
                }
                else -> emitUi(MuzikLinkUiEvent.Error(code, message))
            }
        }

        private fun emitUi(event: MuzikLinkUiEvent) {
            _uiEvents.tryEmit(event)
        }

        // ------------------------------------------------------------------
        // Public actions (UI)
        // ------------------------------------------------------------------

        fun createLink() {
            scope.launch {
                ensureConnected()
                client.sendRegister(deviceName())
            }
        }

        fun joinLink(pairingCode: String) {
            scope.launch {
                ensureConnected()
                client.sendPair(pairingCode, deviceName())
            }
        }

        fun leaveLink() {
            client.sendLeave()
            _linkSession.value = null
            _remoteState.value = null
            stopPlayerRole()
            scope.launch { clearPairingCode() }
        }

        fun handoffTo(targetDeviceId: String) {
            if (!isInLink) return
            client.sendHandoff(targetDeviceId)
        }

        /** Sends a playback command to the active player (controller side). */
        fun sendRemoteCommand(
            action: String,
            trackId: String? = null,
            position: Long? = null,
            volume: Float? = null,
            trackInfo: com.muzikgroup.music.listentogether.TrackInfo? = null,
            insertNext: Boolean? = null,
        ) {
            if (!isInLink || isPlayer) return
            if (listenTogetherActive) return
            client.sendCommand(
                LinkCommandPayload(
                    action = action,
                    trackId = trackId,
                    position = position,
                    volume = volume,
                    trackInfo = trackInfo,
                    insertNext = insertNext,
                    capturedAtServerTime = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun ensureConnected() {
            if (_connectionState.value == LinkConnectionState.CONNECTED) return
            client.connect()
            // Wait briefly for the socket; the register/pair message is queued
            // by the client anyway if the socket opens afterwards.
            val deadline = System.currentTimeMillis() + 5_000
            while (_connectionState.value != LinkConnectionState.CONNECTED && System.currentTimeMillis() < deadline) {
                delay(100)
            }
        }

        // ------------------------------------------------------------------
        // Player integration
        // ------------------------------------------------------------------

        fun setPlayerConnection(connection: PlayerConnection?) {
            stopPlayerRole()
            playerConnection = connection
            if (connection != null && isPlayer) {
                startPlayerRole()
            }
        }

        private fun startPlayerRole() {
            if (listenTogetherActive) {
                Timber.tag(TAG).i("Player role deferred - Listen Together is active")
                return
            }
            val connection = playerConnection ?: return
            stopPlayerRole()

            val listener =
                object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        publishState()
                    }

                    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                        publishState()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            publishState()
                        }
                    }
                }
            playerListener = listener
            try {
                connection.player.addListener(listener)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to attach link player listener")
            }

            volumeJob?.cancel()
            volumeJob =
                scope.launch {
                    connection.service.playerVolume.collectLatest { volume ->
                        if (kotlin.math.abs(volume - lastPublishedVolume) >= VOLUME_PUBLISH_THRESHOLD) {
                            lastPublishedVolume = volume
                            publishState()
                        }
                    }
                }

            heartbeatJob?.cancel()
            heartbeatJob =
                scope.launch {
                    while (true) {
                        if (!isPlayer || listenTogetherActive) break
                        publishState()
                        delay(PUBLISH_HEARTBEAT_MS)
                    }
                }

            // Publish an initial snapshot.
            publishState()
            Timber.tag(TAG).i("Muzik Link player role started")
        }

        private fun stopPlayerRole() {
            heartbeatJob?.cancel()
            heartbeatJob = null
            volumeJob?.cancel()
            volumeJob = null
            playerListener?.let { listener ->
                try {
                    playerConnection?.player?.removeListener(listener)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Failed to detach link player listener")
                }
            }
            playerListener = null
            lastPublishedVolume = -1f
        }

        /** True while we are the player and allowed to publish. */
        private fun canPublish(): Boolean =
            isInLink && isPlayer && !listenTogetherActive && playerConnection != null &&
                System.currentTimeMillis() >= suppressPublishUntilMs

        fun publishState() {
            if (!canPublish()) return
            val connection = playerConnection ?: return
            try {
                val player = connection.player
                val metadata = player.currentMetadata
                val trackInfo =
                    metadata?.let {
                        val durationMs = if (it.duration > 0) it.duration.toLong() * 1000 else 180_000L
                        TrackInfo(
                            id = it.id,
                            title = it.title,
                            artist = it.artists.joinToArtistString(getArtistSeparator(context)) { artist -> artist.name },
                            album = it.album?.title,
                            duration = durationMs,
                            thumbnail = it.thumbnailUrl,
                            suggestedBy = it.suggestedBy,
                        )
                    }
                val queue =
                    try {
                        val currentIndex = connection.currentWindowIndex.value
                        connection.queueWindows.value
                            .drop(currentIndex + 1)
                            .take(50)
                            .map { window -> window.windowToTrackInfo() }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Failed to read link queue snapshot")
                        emptyList()
                    }

                val volume = connection.service.playerVolume.value
                val state =
                    LinkStatePayload(
                        currentTrack = trackInfo,
                        isPlaying = player.isPlaying,
                        position = player.currentPosition.coerceAtLeast(0),
                        lastUpdate = System.currentTimeMillis(),
                        queue = queue,
                        volume = volume,
                        revision = (System.currentTimeMillis() / 1000),
                    )
                lastPublishedVolume = volume
                client.sendState(state)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to publish link state")
            }
        }

        private fun androidx.media3.common.Timeline.Window.windowToTrackInfo(): TrackInfo {
            val metadata = mediaItem.metadata
                ?: return TrackInfo("unknown", "Unknown", "Unknown", "", 0, "")
            val durationMs = if (metadata.duration > 0) metadata.duration.toLong() * 1000 else 180_000L
            return TrackInfo(
                id = metadata.id,
                title = metadata.title,
                artist = metadata.artists.joinToArtistString(getArtistSeparator(context)) { artist -> artist.name },
                album = metadata.album?.title,
                duration = durationMs,
                thumbnail = metadata.thumbnailUrl,
                suggestedBy = metadata.suggestedBy,
            )
        }

        // ------------------------------------------------------------------
        // Command application (player side)
        // ------------------------------------------------------------------

        private fun onCommand(command: LinkCommandPayload) {
            if (!isPlayer || listenTogetherActive) return
            val connection = playerConnection ?: return

            Timber.tag(TAG).d("Applying link command %s from %s", command.action, command.fromDeviceName)
            suppressPublishUntilMs = System.currentTimeMillis() + 500

            connection.allowInternalSync = true
            try {
                when (command.action) {
                    PlaybackActions.PLAY -> {
                        command.position?.let { connection.seekTo(it) }
                        connection.play()
                    }
                    PlaybackActions.PAUSE -> connection.pause()
                    PlaybackActions.SEEK -> command.position?.let { connection.seekTo(it) }
                    PlaybackActions.SKIP_NEXT -> connection.seekToNext()
                    PlaybackActions.SKIP_PREV -> connection.seekToPrevious()
                    PlaybackActions.SET_VOLUME -> {
                        command.volume?.let { connection.service.playerVolume.value = it }
                    }
                    PlaybackActions.CHANGE_TRACK -> applyChangeTrack(connection, command)
                    PlaybackActions.QUEUE_ADD -> applyQueueAdd(connection, command)
                    else -> Timber.tag(TAG).i("Link command %s not supported on Android player yet", command.action)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to apply link command %s", command.action)
            } finally {
                connection.allowInternalSync = false
            }
            // Publish the resulting state so controllers see the effect.
            scope.launch {
                delay(300)
                suppressPublishUntilMs = 0L
                publishState()
            }
        }

        private fun applyChangeTrack(connection: PlayerConnection, command: LinkCommandPayload) {
            val track = command.trackInfo ?: run {
                Timber.tag(TAG).w("CHANGE_TRACK without track info")
                return
            }
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        YouTube.queue(listOf(track.id))
                    }
                result.onSuccess { list ->
                    val preloadItem = list.firstOrNull()?.toMediaMetadata()?.toMediaItem()
                    connection.allowInternalSync = true
                    try {
                        connection.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(videoId = track.id),
                                preloadItem = preloadItem,
                            ),
                        )
                        command.position?.takeIf { it > 0 }?.let { connection.seekTo(it) }
                        command.queueTitle?.let { connection.service.queueTitle = it }
                    } finally {
                        connection.allowInternalSync = false
                    }
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to resolve link track %s", track.id)
                }
            }
        }

        private fun applyQueueAdd(connection: PlayerConnection, command: LinkCommandPayload) {
            val track = command.trackInfo ?: run {
                Timber.tag(TAG).w("QUEUE_ADD without track info")
                return
            }
            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        YouTube.queue(listOf(track.id))
                    }
                result.onSuccess { list ->
                    val mediaItem =
                        list.firstOrNull()?.toMediaMetadata()?.toMediaItem() ?: return@onSuccess
                    connection.allowInternalSync = true
                    try {
                        if (command.insertNext == true) {
                            connection.playNext(mediaItem)
                        } else {
                            connection.addToQueue(mediaItem)
                        }
                    } finally {
                        connection.allowInternalSync = false
                    }
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to resolve link track %s", track.id)
                }
            }
        }

        // ------------------------------------------------------------------
        // Handoff
        // ------------------------------------------------------------------

        private fun onHandoffStart(event: MuzikLinkEvent.HandoffStart) {
            val connection = playerConnection
            if (connection == null || listenTogetherActive) {
                Timber.tag(TAG).w("Handoff start ignored (no player or Listen Together active)")
                return
            }
            val state = event.payload.state
            _linkSession.value = _linkSession.value?.copy(playerDeviceId = _linkSession.value?.deviceId ?: "")
            startPlayerRole()

            val track = state?.currentTrack ?: run {
                Timber.tag(TAG).i("Handoff start without track - becoming idle player")
                return
            }

            scope.launch {
                val result =
                    withContext(Dispatchers.IO) {
                        YouTube.queue(listOf(track.id))
                    }
                result.onSuccess { list ->
                    val preloadItem = list.firstOrNull()?.toMediaMetadata()?.toMediaItem()
                    connection.allowInternalSync = true
                    try {
                        connection.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(videoId = track.id),
                                preloadItem = preloadItem,
                            ),
                        )
                        // Wait for readiness so the seek lands.
                        val deadline = System.currentTimeMillis() + HANDOFF_READY_TIMEOUT_MS
                        while (System.currentTimeMillis() < deadline &&
                            connection.player.playbackState != Player.STATE_READY
                        ) {
                            delay(50)
                        }
                        state.position.takeIf { it > 0 }?.let { connection.seekTo(it) }
                        if (state.isPlaying) {
                            connection.play()
                        } else {
                            connection.pause()
                        }
                    } finally {
                        connection.allowInternalSync = false
                    }
                    publishState()
                    emitUi(MuzikLinkUiEvent.Info("Playback taken over on this device"))
                }.onFailure { e ->
                    Timber.tag(TAG).w(e, "Handoff track resolution failed for %s", track.id)
                    emitUi(MuzikLinkUiEvent.Error("handoff_failed", "Could not resume playback on this device"))
                }
            }
        }

        private fun onLinkStop(event: MuzikLinkEvent.Stop) {
            val connection = playerConnection ?: return
            try {
                connection.allowInternalSync = true
                try {
                    connection.pause()
                } finally {
                    connection.allowInternalSync = false
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to pause after link stop")
            }
            stopPlayerRole()
            emitUi(MuzikLinkUiEvent.Info("Playback moved to another device"))
        }

        fun close() {
            stopPlayerRole()
            eventCollectorJob?.cancel()
            client.disconnect()
        }
    }
