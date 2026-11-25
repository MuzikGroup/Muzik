/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.listentogether

import com.muzik.shared.listentogether.network.LinkCommandPayload
import com.muzik.shared.listentogether.network.LinkDeviceInfo
import com.muzik.shared.listentogether.network.LinkDeviceJoinedPayload
import com.muzik.shared.listentogether.network.LinkDeviceLeftPayload
import com.muzik.shared.listentogether.network.LinkDeviceTypes
import com.muzik.shared.listentogether.network.LinkHandoffPayload
import com.muzik.shared.listentogether.network.LinkHandoffStartPayload
import com.muzik.shared.listentogether.network.LinkMessageTypes
import com.muzik.shared.listentogether.network.LinkPairPayload
import com.muzik.shared.listentogether.network.LinkPlayerChangedPayload
import com.muzik.shared.listentogether.network.LinkRegisterPayload
import com.muzik.shared.listentogether.network.LinkSessionPayload
import com.muzik.shared.listentogether.network.LinkStatePayload
import com.muzik.shared.listentogether.network.LinkStopPayload
import com.muzik.shared.listentogether.network.ListenTogetherMessageCodec
import com.muzik.shared.listentogether.network.MessageTypes
import com.muzik.shared.listentogether.network.ErrorPayload
import com.muzik.shared.listentogether.network.PlaybackActions
import com.muzik.shared.listentogether.network.isAllowedListenTogetherServerUrl
import com.muzik.shared.listentogether.network.TrackInfo
import com.muzik.shared.listentogether.network.createListenTogetherMessageCodec
import com.muzik.shared.model.SongItem
import com.muzik.shared.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-visible Muzik Link events for the UI. */
sealed class MuzikLinkUiEvent {
    data class Error(val code: String, val message: String) : MuzikLinkUiEvent()
    data class Info(val message: String) : MuzikLinkUiEvent()
}

enum class LinkConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/**
 * Relay-based device handoff for Muzik Link: pairs the user's own devices
 * through the shared websocket server. One device is the active player and
 * publishes its playback state; every other device remote-controls it and can
 * take playback over. Shares the [ListenTogetherPlayback] port for all actual
 * playback changes, so remote control and handoff behave exactly like a
 * Listen Together guest would.
 */
class MuzikLinkManager(
    private val callerScope: CoroutineScope,
    private val httpClient: HttpClient,
    private val playback: ListenTogetherPlayback,
    private val resolver: ListenTogetherTrackResolver,
    private val deviceNameProvider: () -> String,
    private val deviceTypeProvider: () -> String = { LinkDeviceTypes.DESKTOP },
    private val appVersionProvider: () -> String = { "" },
    private val monotonicClockMs: () -> Long,
    private val wallClockMs: () -> Long,
    private val logger: ListenTogetherLogger = ListenTogetherLogger { _, _ -> },
    private val codecDispatcher: CoroutineDispatcher,
    private val isListenTogetherActive: () -> Boolean = { false },
    private val reconnectDelayMillis: (attempt: Int) -> Long = { attempt ->
        (1_000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
    },
) {
    companion object {
        const val DEFAULT_SERVER_URL = "wss://muzzserverx.meowery.eu/ws"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val PUBLISH_HEARTBEAT_MS = 2_000L
        private const val VOLUME_PUBLISH_THRESHOLD = 0.01f
        private const val MAX_DEVICES = 8
    }

    private val _connectionState = MutableStateFlow(LinkConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LinkConnectionState> = _connectionState

    private val _linkSession = MutableStateFlow<LinkSessionPayload?>(null)
    val linkSession: StateFlow<LinkSessionPayload?> = _linkSession

    /** The active player's last published state, as seen by controllers. */
    private val _remoteState = MutableStateFlow<LinkStatePayload?>(null)
    val remoteState: StateFlow<LinkStatePayload?> = _remoteState

    private val _uiEvents = MutableSharedFlow<MuzikLinkUiEvent>(extraBufferCapacity = 32)
    val uiEvents: SharedFlow<MuzikLinkUiEvent> = _uiEvents

    val isInLink: Boolean
        get() = _linkSession.value != null

    val isPlayer: Boolean
        get() {
            val session = _linkSession.value ?: return false
            return session.playerDeviceId == session.deviceId
        }

    private var session: WebSocketSession? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var publisherJob: Job? = null
    private var volumeJob: Job? = null
    private var reconnectAttempts = 0
    private var userRequestedDisconnect = false
    private var connectGeneration = 0

    @Volatile
    private var lastPublishedVolume = -1f

    init {
        callerScope.launch {
            val saved = Settings.shared.muzikLinkPairingCode
            if (saved.isNotBlank()) {
                connect()
            }
        }
    }

    // ------------------------------------------------------------------
    // Public actions
    // ------------------------------------------------------------------

    fun connect() {
        if (_connectionState.value != LinkConnectionState.DISCONNECTED) return
        userRequestedDisconnect = false
        reconnectAttempts = 0
        connectionJob?.cancel()
        connectionJob =
            callerScope.launch {
                runConnection()
            }
    }

    fun createLink() {
        callerScope.launch {
            ensureConnected()
            send(
                LinkMessageTypes.LINK_REGISTER,
                LinkRegisterPayload(
                    deviceName = deviceNameProvider().ifBlank { "Desktop" },
                    deviceType = deviceTypeProvider(),
                    appVersion = appVersionProvider(),
                ),
            )
        }
    }

    fun joinLink(pairingCode: String) {
        callerScope.launch {
            ensureConnected()
            send(
                LinkMessageTypes.LINK_PAIR,
                LinkPairPayload(
                    pairingCode = pairingCode.trim().uppercase(),
                    deviceName = deviceNameProvider().ifBlank { "Desktop" },
                    deviceType = deviceTypeProvider(),
                    appVersion = appVersionProvider(),
                ),
            )
        }
    }

    fun leaveLink() {
        userRequestedDisconnect = true
        launchSend(LinkMessageTypes.LINK_LEAVE, null)
        _linkSession.value = null
        _remoteState.value = null
        stopPublisher()
        Settings.shared.muzikLinkPairingCode = ""
        connectionJob?.cancel()
        connectionJob = null
        _connectionState.value = LinkConnectionState.DISCONNECTED
        session = null
    }

    fun handoffTo(targetDeviceId: String) {
        if (!isInLink) return
        launchSend(LinkMessageTypes.LINK_HANDOFF, LinkHandoffPayload(targetDeviceId))
    }

    /** Sends a playback command to the active player (controller side). */
    fun sendRemoteCommand(
        action: String,
        trackId: String? = null,
        position: Long? = null,
        volume: Float? = null,
        trackInfo: TrackInfo? = null,
        insertNext: Boolean? = null,
        queueTitle: String? = null,
    ) {
        if (!isInLink || isPlayer) return
        if (isListenTogetherActive()) return
        launchSend(
            LinkMessageTypes.LINK_COMMAND,
            LinkCommandPayload(
                action = action,
                trackId = trackId,
                position = position,
                volume = volume,
                trackInfo = trackInfo,
                insertNext = insertNext,
                queueTitle = queueTitle,
                capturedAtServerTime = wallClockMs(),
                fromDeviceId = _linkSession.value?.deviceId,
            ),
        )
    }

    suspend fun close() {
        userRequestedDisconnect = true
        connectionJob?.cancel()
        reconnectJob?.cancel()
        stopPublisher()
        volumeJob?.cancel()
        try {
            session?.close(CloseReason(CloseReason.Codes.NORMAL, "Muzik Link closed"))
        } catch (_: Exception) {
        }
        session = null
        _connectionState.value = LinkConnectionState.DISCONNECTED
    }

    // ------------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------------

    private suspend fun ensureConnected() {
        if (_connectionState.value == LinkConnectionState.CONNECTED && session != null) return
        connect()
        val deadline = monotonicClockMs() + 5_000
        while ((_connectionState.value != LinkConnectionState.CONNECTED || session == null) && monotonicClockMs() < deadline) {
            delay(100)
        }
    }

    private suspend fun runConnection() {
        connectGeneration++
        val generation = connectGeneration
        reconnectJob?.cancel()
        _connectionState.value = LinkConnectionState.CONNECTING

        val serverUrl =
            Settings.shared.listenTogetherServerUrl
                .takeIf { it.isNotBlank() && isAllowedListenTogetherServerUrl(it) }
                ?: DEFAULT_SERVER_URL

        try {
            val wsSession = httpClient.webSocketSession(urlString = serverUrl)
            if (connectGeneration != generation) {
                runCatching { wsSession.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Stale link connection")) }
                return
            }
            session = wsSession
            _connectionState.value = LinkConnectionState.CONNECTED
            reconnectAttempts = 0
            log(ListenTogetherLogLevel.DEBUG, "Muzik Link connected to $serverUrl")

            // Rejoin the remembered group silently.
            val savedCode = Settings.shared.muzikLinkPairingCode
            if (savedCode.isNotBlank()) {
                send(
                    LinkMessageTypes.LINK_PAIR,
                    LinkPairPayload(
                        pairingCode = savedCode.uppercase(),
                        deviceName = deviceNameProvider().ifBlank { "Desktop" },
                        deviceType = deviceTypeProvider(),
                        appVersion = appVersionProvider(),
                    ),
                )
            }

            for (frame in wsSession.incoming) {
                if (frame is Frame.Binary || frame is Frame.Text) {
                    handleFrame(frame.readBytes(), generation)
                }
            }
            handleDown(generation, "server closed the socket")
        } catch (e: Exception) {
            if (connectGeneration == generation) {
                log(ListenTogetherLogLevel.WARNING, "Muzik Link connection failed: ${e.message}")
                handleDown(generation, "connection error")
            }
        }
    }

    private fun handleDown(generation: Int, reason: String) {
        if (connectGeneration != generation) return
        session = null
        _connectionState.value = LinkConnectionState.DISCONNECTED
        if (userRequestedDisconnect) return

        // The link group disappears for this device; surfaces the state and
        // reconnects if a pairing code is remembered.
        _linkSession.value = null
        _remoteState.value = null
        stopPublisher()

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log(ListenTogetherLogLevel.WARNING, "Muzik Link gave up reconnecting ($reason)")
            return
        }
        reconnectJob?.cancel()
        reconnectJob =
            callerScope.launch {
                delay(reconnectDelayMillis(reconnectAttempts))
                reconnectAttempts++
                connect()
            }
    }

    // ------------------------------------------------------------------
    // Wire handling
    // ------------------------------------------------------------------

    private suspend fun send(msgType: String, payload: Any?) {
        val ws = session ?: return
        val data =
            withContext(codecDispatcher) {
                createListenTogetherMessageCodec()
                    .apply { compressionEnabled = true }
                    .encode(msgType, payload)
            }
        try {
            ws.send(Frame.Binary(true, data))
        } catch (e: Exception) {
            log(ListenTogetherLogLevel.WARNING, "Failed to send $msgType: ${e.message}")
        }
    }

    private fun launchSend(msgType: String, payload: Any?) {
        callerScope.launch {
            send(msgType, payload)
        }
    }

    private suspend fun handleFrame(data: ByteArray, generation: Int) {
        val envelope =
            try {
                withContext(codecDispatcher) {
                    createListenTogetherMessageCodec()
                        .apply { compressionEnabled = true }
                        .decodeEnvelope(data)
                }
            } catch (e: Exception) {
                log(ListenTogetherLogLevel.DEBUG, "Dropping malformed link frame: ${e.message}")
                return
            }
        val payload =
            try {
                withContext(codecDispatcher) {
                    createListenTogetherMessageCodec()
                        .apply { compressionEnabled = true }
                        .decodePayload(envelope.type, envelope.payload)
                }
            } catch (e: Exception) {
                log(ListenTogetherLogLevel.WARNING, "Failed to decode ${envelope.type}: ${e.message}")
                return
            }
        if (connectGeneration != generation) return

        when (envelope.type) {
            LinkMessageTypes.LINK_REGISTERED -> handleSession(payload as? LinkSessionPayload, created = true)
            LinkMessageTypes.LINK_PAIRED -> handleSession(payload as? LinkSessionPayload, created = false)
            LinkMessageTypes.LINK_DEVICE_JOINED -> {
                val joined = payload as? LinkDeviceJoinedPayload ?: return
                updateDevices { current ->
                    current.copy(devices = current.devices.filterNot { it.deviceId == joined.device.deviceId } + joined.device)
                }
                emitUi(MuzikLinkUiEvent.Info("${joined.device.deviceName} joined the link"))
            }
            LinkMessageTypes.LINK_DEVICE_LEFT -> {
                val left = payload as? LinkDeviceLeftPayload ?: return
                updateDevices { current ->
                    current.copy(
                        devices = current.devices.filterNot { it.deviceId == left.deviceId },
                        playerDeviceId = if (left.wasPlayer) "" else current.playerDeviceId,
                    )
                }
                if (left.wasPlayer) {
                    _remoteState.value = null
                    stopPublisher()
                }
                emitUi(MuzikLinkUiEvent.Info("A device left the link"))
            }
            LinkMessageTypes.LINK_PLAYER_CHANGED -> {
                val changed = payload as? LinkPlayerChangedPayload ?: return
                updateDevices { current ->
                    current.copy(playerDeviceId = changed.newPlayerDeviceId)
                }
                if (changed.newPlayerDeviceId == _linkSession.value?.deviceId) {
                    startPublisher()
                } else {
                    stopPublisher()
                    _remoteState.value = null
                }
            }
            LinkMessageTypes.LINK_STATE -> {
                (payload as? LinkStatePayload)?.let { _remoteState.value = it }
            }
            LinkMessageTypes.LINK_COMMAND -> {
                (payload as? LinkCommandPayload)?.let { applyCommand(it) }
            }
            LinkMessageTypes.LINK_HANDOFF_START -> {
                (payload as? LinkHandoffStartPayload)?.let { applyHandoffStart(it) }
            }
            LinkMessageTypes.LINK_STOP -> applyLinkStop()
            MessageTypes.ERROR -> {
                val error = payload as? ErrorPayload ?: return
                if (error.code == "link_not_found") {
                    Settings.shared.muzikLinkPairingCode = ""
                    _linkSession.value = null
                }
                emitUi(MuzikLinkUiEvent.Error(error.code, error.message))
            }
        }
    }

    private inline fun updateDevices(update: (LinkSessionPayload) -> LinkSessionPayload) {
        val current = _linkSession.value ?: return
        _linkSession.value = update(current)
    }

    private fun handleSession(
        sessionPayload: LinkSessionPayload?,
        created: Boolean,
    ) {
        if (sessionPayload == null) return
        _linkSession.value = sessionPayload
        Settings.shared.muzikLinkPairingCode = sessionPayload.pairingCode
        if (!isPlayer) {
            sessionPayload.state?.let { _remoteState.value = it }
        }
        if (isPlayer) {
            startPublisher()
        } else {
            stopPublisher()
        }
        emitUi(
            MuzikLinkUiEvent.Info(
                if (created) {
                    "Link created - pairing code ${sessionPayload.pairingCode}"
                } else {
                    "Paired with link"
                },
            ),
        )
    }

    private fun emitUi(event: MuzikLinkUiEvent) {
        _uiEvents.tryEmit(event)
    }

    // ------------------------------------------------------------------
    // Player role
    // ------------------------------------------------------------------

    private fun startPublisher() {
        if (isListenTogetherActive()) {
            log(ListenTogetherLogLevel.WARNING, "Muzik Link player role deferred - Listen Together is active")
            return
        }
        stopPublisher()

        publisherJob =
            callerScope.launch {
                observeHostPlayback(
                    changes = playback.playing,
                    intervalMs = PUBLISH_HEARTBEAT_MS,
                    publish = { publishState() },
                )
            }
        volumeJob =
            callerScope.launch {
                playback.volume.collectLatest { volume ->
                    if (kotlin.math.abs(volume - lastPublishedVolume) >= VOLUME_PUBLISH_THRESHOLD) {
                        lastPublishedVolume = volume
                        publishState()
                    }
                }
            }
        callerScope.launch {
            publishState()
        }
        log(ListenTogetherLogLevel.DEBUG, "Muzik Link player role started")
    }

    private fun stopPublisher() {
        publisherJob?.cancel()
        publisherJob = null
        volumeJob?.cancel()
        volumeJob = null
        lastPublishedVolume = -1f
    }

    private fun canPublish(): Boolean =
        isInLink && isPlayer && !isListenTogetherActive()

    fun publishState() {
        if (!canPublish()) return
        val state = buildStateSnapshot() ?: return
        launchSend(LinkMessageTypes.LINK_STATE, state)
    }

    private fun buildStateSnapshot(): LinkStatePayload? {
        val song = playback.currentSong.value
        val index = playback.currentQueueIndex()
        val upcoming =
            playback.queue.value
                .drop((index ?: -1) + 1)
                .take(50)
                .map { it.toLinkTrackInfo() }
        return LinkStatePayload(
            currentTrack = song?.toLinkTrackInfo(),
            isPlaying = playback.playing.value,
            position = playback.currentPositionMs().coerceAtLeast(0),
            lastUpdate = wallClockMs(),
            queue = upcoming,
            volume = playback.volume.value,
            revision = wallClockMs() / 1_000,
        )
    }

    private fun SongItem.toLinkTrackInfo(): TrackInfo =
        TrackInfo(
            id = id,
            title = title,
            artist = artists.joinToString(", ") { it.name },
            album = album?.name,
            duration = (duration ?: 0L) * 1_000L,
            thumbnail = thumbnail,
        )

    // ------------------------------------------------------------------
    // Command application (player side)
    // ------------------------------------------------------------------

    private fun applyCommand(command: LinkCommandPayload) {
        if (!canApplyRemoteInput()) return
        log(ListenTogetherLogLevel.DEBUG, "Applying link command ${command.action} from ${command.fromDeviceName}")

        callerScope.launch {
            try {
                when (command.action) {
                    PlaybackActions.PLAY -> {
                        command.position?.let { playback.seekToFromRemote(it) }
                        playback.setPlayingFromRemote(true)
                    }
                    PlaybackActions.PAUSE -> {
                        playback.setPlayingFromRemote(false)
                        command.position?.let { playback.seekToFromRemote(it) }
                    }
                    PlaybackActions.SEEK -> {
                        command.position?.let { playback.seekToFromRemote(it) }
                    }
                    PlaybackActions.SKIP_NEXT -> playback.skipToNextFromRemote()
                    PlaybackActions.SKIP_PREV -> playback.skipToPreviousFromRemote()
                    PlaybackActions.SET_VOLUME -> {
                        command.volume?.let { playback.setVolumeFromRemote(it) }
                    }
                    PlaybackActions.CHANGE_TRACK -> applyChangeTrack(command.trackInfo, command.position)
                    PlaybackActions.QUEUE_ADD -> applyQueueAdd(command.trackInfo)
                    else -> log(ListenTogetherLogLevel.DEBUG, "Link command ${command.action} not supported yet")
                }
            } catch (e: Exception) {
                log(ListenTogetherLogLevel.WARNING, "Failed to apply ${command.action}: ${e.message}")
            }
            publishState()
        }
    }

    private fun canApplyRemoteInput(): Boolean =
        isInLink && isPlayer && !isListenTogetherActive()

    private fun applyChangeTrack(
        track: TrackInfo?,
        position: Long?,
    ) {
        if (track == null) return
        callerScope.launch {
            val song = resolver.resolve(track) ?: track.toSongItem(omitNonPositiveDuration = true)
            playback.playQueueFromRemote(listOf(song), 0)
            position?.takeIf { it > 0 }?.let { playback.seekToFromRemote(it) }
            playback.setPlayingFromRemote(true)
        }
    }

    private fun applyQueueAdd(track: TrackInfo?) {
        if (track == null) return
        callerScope.launch {
            val song = resolver.resolve(track) ?: track.toSongItem(omitNonPositiveDuration = true)
            playback.addToQueueFromRemote(song)
        }
    }

    // ------------------------------------------------------------------
    // Handoff
    // ------------------------------------------------------------------

    private fun applyHandoffStart(payload: LinkHandoffStartPayload) {
        if (isListenTogetherActive()) {
            log(ListenTogetherLogLevel.WARNING, "Handoff start ignored - Listen Together is active")
            return
        }
        startPublisher()

        val state = payload.state
        val track = state?.currentTrack ?: run {
            emitUi(MuzikLinkUiEvent.Info("Playback taken over on this device"))
            return
        }

        callerScope.launch {
            try {
                val song = resolver.resolve(track) ?: track.toSongItem(omitNonPositiveDuration = true)
                playback.playQueueFromRemote(listOf(song), 0)
                state.position.takeIf { it > 0 }?.let { playback.seekToFromRemote(it) }
                playback.setPlayingFromRemote(state.isPlaying)
                publishState()
                emitUi(MuzikLinkUiEvent.Info("Playback taken over on this device"))
            } catch (e: Exception) {
                log(ListenTogetherLogLevel.WARNING, "Handoff resume failed: ${e.message}")
                emitUi(MuzikLinkUiEvent.Error("handoff_failed", "Could not resume playback on this device"))
            }
        }
    }

    private fun applyLinkStop() {
        if (isListenTogetherActive()) return
        callerScope.launch {
            try {
                playback.setPlayingFromRemote(false)
            } catch (_: Exception) {
            }
        }
        stopPublisher()
        emitUi(MuzikLinkUiEvent.Info("Playback moved to another device"))
    }

    private fun log(
        level: ListenTogetherLogLevel,
        message: String,
    ) {
        logger.log(level, message)
    }
}
