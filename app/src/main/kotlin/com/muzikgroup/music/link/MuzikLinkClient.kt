/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzikgroup.music.link

import android.content.Context
import com.muzikgroup.music.constants.ListenTogetherServerUrlKey
import com.muzikgroup.music.listentogether.ErrorPayload
import com.muzikgroup.music.listentogether.LinkCommandPayload
import com.muzikgroup.music.listentogether.LinkDeviceJoinedPayload
import com.muzikgroup.music.listentogether.LinkDeviceLeftPayload
import com.muzikgroup.music.listentogether.LinkDeviceInfo
import com.muzikgroup.music.listentogether.LinkDeviceTypes
import com.muzikgroup.music.listentogether.LinkHandoffPayload
import com.muzikgroup.music.listentogether.LinkHandoffStartPayload
import com.muzikgroup.music.listentogether.LinkMessageTypes
import com.muzikgroup.music.listentogether.LinkPairPayload
import com.muzikgroup.music.listentogether.LinkPlayerChangedPayload
import com.muzikgroup.music.listentogether.LinkRegisterPayload
import com.muzikgroup.music.listentogether.LinkSessionPayload
import com.muzikgroup.music.listentogether.LinkStatePayload
import com.muzikgroup.music.listentogether.LinkStopPayload
import com.muzikgroup.music.listentogether.ListenTogetherServers
import com.muzikgroup.music.listentogether.MessageCodec
import com.muzikgroup.music.listentogether.MessageTypes
import com.muzikgroup.music.listentogether.PingPayload
import com.muzikgroup.music.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.of
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Connection lifecycle of the Muzik Link websocket. */
enum class LinkConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/** Events decoded off the wire, consumed by [MuzikLinkManager]. */
sealed class MuzikLinkEvent {
    data object Connected : MuzikLinkEvent()
    data object Disconnected : MuzikLinkEvent()
    data class Registered(val session: LinkSessionPayload) : MuzikLinkEvent()
    data class Paired(val session: LinkSessionPayload) : MuzikLinkEvent()
    data class DeviceJoined(val device: LinkDeviceInfo) : MuzikLinkEvent()
    data class DeviceLeft(val deviceId: String, val wasPlayer: Boolean) : MuzikLinkEvent()
    data class Command(val command: LinkCommandPayload) : MuzikLinkEvent()
    data class StateUpdated(val state: LinkStatePayload) : MuzikLinkEvent()
    data class HandoffStart(val payload: LinkHandoffStartPayload) : MuzikLinkEvent()
    data class Stop(val payload: LinkStopPayload) : MuzikLinkEvent()
    data class PlayerChanged(val newPlayerId: String, val newPlayerName: String) : MuzikLinkEvent()
    data class ServerError(val code: String, val message: String) : MuzikLinkEvent()
}

/**
 * Lean websocket client for Muzik Link. Shares the relay server (and the
 * protobuf codec) with Listen Together, but owns an independent connection so
 * the link works while no Listen Together room is joined.
 */
class MuzikLinkClient(
    private val context: Context,
) {
    companion object {
        private const val TAG = "MuzikLinkClient"
        private const val PING_INTERVAL_MS = 25_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val codec = MessageCodec(compressionEnabled = true)
    private val okHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(60, TimeUnit.SECONDS)
            .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private var userRequestedDisconnect = false

    private val _connectionState = MutableStateFlow(LinkConnectionState.DISCONNECTED)
    val connectionState: StateFlow<LinkConnectionState> = _connectionState

    private val _events =
        MutableSharedFlow<MuzikLinkEvent>(
            replay = 0,
            extraBufferCapacity = 64,
        )
    val events: SharedFlow<MuzikLinkEvent> = _events

    /** Pairing code remembered so the client silently re-pairs after a drop. */
    @Volatile
    var autoPairCode: String? = null

    private suspend fun resolveServerUrl(): String {
        val preferred = context.dataStore.data.first()[ListenTogetherServerUrlKey]
        return preferred?.takeIf { it.isNotBlank() } ?: ListenTogetherServers.defaultServerUrl
    }

    fun connect() {
        if (_connectionState.value != LinkConnectionState.DISCONNECTED) return
        userRequestedDisconnect = false
        reconnectAttempts = 0
        scope.launch { openSocket() }
    }

    fun disconnect() {
        userRequestedDisconnect = true
        cancelJobs()
        webSocket?.close(1000, null)
        webSocket = null
        _connectionState.value = LinkConnectionState.DISCONNECTED
    }

    private suspend fun openSocket() {
        val url = try {
            resolveServerUrl()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to resolve link server URL")
            scheduleReconnect()
            return
        }

        val request =
            Request.Builder()
                .url(url)
                .header("User-Agent", context.packageName)
                .build()

        _connectionState.value = LinkConnectionState.CONNECTING
        webSocket =
            okHttpClient.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Timber.tag(TAG).i("Link socket connected to %s", url)
                        reconnectAttempts = 0
                        _connectionState.value = LinkConnectionState.CONNECTED
                        startPingLoop()
                        emit(MuzikLinkEvent.Connected)
                        autoPairCode?.let { code ->
                            // Try to rejoin the remembered group silently.
                            sendPair(code, linkDeviceName(context))
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        handleBytes(bytes.toByteArray())
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        handleDown("closed ($code)")
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Timber.tag(TAG).w(t, "Link socket failure")
                        handleDown("failure")
                    }
                },
            )
    }

    private fun handleDown(reason: String) {
        cancelJobs()
        webSocket = null
        _connectionState.value = LinkConnectionState.DISCONNECTED
        emit(MuzikLinkEvent.Disconnected)
        if (!userRequestedDisconnect) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (userRequestedDisconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.tag(TAG).w("Max link reconnect attempts reached")
            return
        }
        reconnectJob?.cancel()
        reconnectJob =
            scope.launch {
                val backoff =
                    minOf(INITIAL_BACKOFF_MS * (1L shl reconnectAttempts.coerceAtMost(5)), MAX_BACKOFF_MS)
                delay(backoff)
                reconnectAttempts++
                openSocket()
            }
    }

    private fun cancelJobs() {
        pingJob?.cancel()
        pingJob = null
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob =
            scope.launch {
                while (true) {
                    delay(PING_INTERVAL_MS)
                    try {
                        send(MessageTypes.PING, PingPayload(clientTime = System.currentTimeMillis(), sequence = 0))
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Link ping failed")
                    }
                }
            }
    }

    private fun handleBytes(data: ByteArray) {
        try {
            val (msgType, payloadBytes) = codec.decode(data)
            if (msgType == MessageTypes.PONG) return
            when (msgType) {
                LinkMessageTypes.LINK_REGISTERED -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkSessionPayload ?: return
                    emit(MuzikLinkEvent.Registered(payload))
                }
                LinkMessageTypes.LINK_PAIRED -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkSessionPayload ?: return
                    emit(MuzikLinkEvent.Paired(payload))
                }
                LinkMessageTypes.LINK_DEVICE_JOINED -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkDeviceJoinedPayload ?: return
                    emit(MuzikLinkEvent.DeviceJoined(payload.device))
                }
                LinkMessageTypes.LINK_DEVICE_LEFT -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkDeviceLeftPayload ?: return
                    emit(MuzikLinkEvent.DeviceLeft(payload.deviceId, payload.wasPlayer))
                }
                LinkMessageTypes.LINK_COMMAND -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkCommandPayload ?: return
                    emit(MuzikLinkEvent.Command(payload))
                }
                LinkMessageTypes.LINK_STATE -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkStatePayload ?: return
                    emit(MuzikLinkEvent.StateUpdated(payload))
                }
                LinkMessageTypes.LINK_HANDOFF_START -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkHandoffStartPayload ?: return
                    emit(MuzikLinkEvent.HandoffStart(payload))
                }
                LinkMessageTypes.LINK_STOP -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkStopPayload ?: return
                    emit(MuzikLinkEvent.Stop(payload))
                }
                LinkMessageTypes.LINK_PLAYER_CHANGED -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? LinkPlayerChangedPayload ?: return
                    emit(MuzikLinkEvent.PlayerChanged(payload.newPlayerDeviceId, payload.newPlayerDeviceName))
                }
                MessageTypes.ERROR -> {
                    val payload = codec.decodePayload(msgType, payloadBytes) as? ErrorPayload ?: return
                    emit(MuzikLinkEvent.ServerError(payload.code, payload.message))
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to handle link message")
        }
    }

    private fun emit(event: MuzikLinkEvent) {
        _events.tryEmit(event)
    }

    private fun send(msgType: String, payload: Any?) {
        val socket = webSocket ?: return
        try {
            val data = codec.encode(msgType, payload)
            socket.send(of(*data))
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to send %s", msgType)
        }
    }

    fun sendRegister(deviceName: String, deviceType: String = LinkDeviceTypes.ANDROID, appVersion: String = deviceVersion()) {
        autoPairCode = null
        send(LinkMessageTypes.LINK_REGISTER, LinkRegisterPayload(deviceName, deviceType, appVersion))
    }

    fun sendPair(pairingCode: String, deviceName: String, deviceType: String = LinkDeviceTypes.ANDROID, appVersion: String = deviceVersion()) {
        val code = pairingCode.trim().uppercase()
        autoPairCode = code
        send(LinkMessageTypes.LINK_PAIR, LinkPairPayload(code, deviceName, deviceType, appVersion))
    }

    fun sendLeave() {
        autoPairCode = null
        send(LinkMessageTypes.LINK_LEAVE, null)
    }

    fun sendCommand(command: LinkCommandPayload) {
        send(LinkMessageTypes.LINK_COMMAND, command)
    }

    fun sendState(state: LinkStatePayload) {
        send(LinkMessageTypes.LINK_STATE, state)
    }

    fun sendHandoff(targetDeviceId: String) {
        send(LinkMessageTypes.LINK_HANDOFF, LinkHandoffPayload(targetDeviceId))
    }

    private fun deviceVersion(): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }

    /** Human-readable name for this phone/tablet, overridable by the user. */
    fun linkDeviceName(context: Context): String =
        android.provider.Settings.Global.getString(
            context.contentResolver,
            android.provider.Settings.Global.DEVICE_NAME,
        )?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL.takeIf { it.isNotBlank() }
            ?: "Android device"
}
