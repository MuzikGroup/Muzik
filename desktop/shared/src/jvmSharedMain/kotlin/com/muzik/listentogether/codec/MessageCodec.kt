/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.listentogether.codec

import com.muzik.music.listentogether.proto.Listentogether
import com.muzik.shared.listentogether.network.ApproveJoinPayload
import com.muzik.shared.listentogether.network.ApproveSuggestionPayload
import com.muzik.shared.listentogether.network.BufferCompletePayload
import com.muzik.shared.listentogether.network.BufferReadyPayload
import com.muzik.shared.listentogether.network.BufferWaitPayload
import com.muzik.shared.listentogether.network.ClientCapabilities
import com.muzik.shared.listentogether.network.CreateRoomPayload
import com.muzik.shared.listentogether.network.ErrorPayload
import com.muzik.shared.listentogether.network.HostChangedPayload
import com.muzik.shared.listentogether.network.JoinApprovedPayload
import com.muzik.shared.listentogether.network.JoinRejectedPayload
import com.muzik.shared.listentogether.network.JoinRequestPayload
import com.muzik.shared.listentogether.network.JoinRoomPayload
import com.muzik.shared.listentogether.network.KickUserPayload
import com.muzik.shared.listentogether.network.KickedPayload
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
import com.muzik.shared.listentogether.network.ListenTogetherEnvelope
import com.muzik.shared.listentogether.network.ListenTogetherMessageCodec
import com.muzik.shared.listentogether.network.MessageTypes
import com.muzik.shared.listentogether.network.PingPayload
import com.muzik.shared.listentogether.network.PlaybackActions
import com.muzik.shared.listentogether.network.PlaybackActionPayload
import com.muzik.shared.listentogether.network.PongPayload
import com.muzik.shared.listentogether.network.ReconnectPayload
import com.muzik.shared.listentogether.network.ReconnectedPayload
import com.muzik.shared.listentogether.network.RejectJoinPayload
import com.muzik.shared.listentogether.network.RejectSuggestionPayload
import com.muzik.shared.listentogether.network.RoomCreatedPayload
import com.muzik.shared.listentogether.network.RoomState
import com.muzik.shared.listentogether.network.ServerCapabilities
import com.muzik.shared.listentogether.network.ServerRestartingPayload
import com.muzik.shared.listentogether.network.SuggestTrackPayload
import com.muzik.shared.listentogether.network.SuggestionApprovedPayload
import com.muzik.shared.listentogether.network.SuggestionReceivedPayload
import com.muzik.shared.listentogether.network.SuggestionRejectedPayload
import com.muzik.shared.listentogether.network.SuggestionSubmittedPayload
import com.muzik.shared.listentogether.network.SyncStatePayload
import com.muzik.shared.listentogether.network.TrackInfo
import com.muzik.shared.listentogether.network.TransferHostPayload
import com.muzik.shared.listentogether.network.UserDisconnectedPayload
import com.muzik.shared.listentogether.network.UserInfo
import com.muzik.shared.listentogether.network.UserJoinedPayload
import com.muzik.shared.listentogether.network.UserLeftPayload
import com.muzik.shared.listentogether.network.UserReconnectedPayload
import com.muzik.shared.listentogether.network.knownMessageTypes
import com.muzik.shared.listentogether.network.payloadMatchesMessageType
import com.muzik.shared.listentogether.network.payloadlessMessageTypes
import com.muzik.shared.listentogether.network.validateRoomResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class MessageCodec(
    override var compressionEnabled: Boolean = false,
) : ListenTogetherMessageCodec {
    companion object {
        private const val COMPRESSION_THRESHOLD = 100
        private const val MAX_ENVELOPE_BYTES = 256 * 1024
        private const val MAX_PAYLOAD_BYTES = 2 * 1024 * 1024
    }

    override fun encode(
        msgType: String,
        payload: Any?,
        requestId: Long,
    ): ByteArray = encodeProtobuf(msgType, payload, requestId)

    override fun decodeEnvelope(data: ByteArray): ListenTogetherEnvelope = decodeProtobuf(data)

    private fun encodeProtobuf(
        msgType: String,
        payload: Any?,
        requestId: Long,
    ): ByteArray {
        require(msgType in knownMessageTypes) { "Unsupported Listen Together message type: $msgType" }
        require(payloadMatchesMessageType(msgType, payload)) { "Invalid payload for Listen Together message type: $msgType" }
        var payloadBytes = byteArrayOf()
        var compressed = false

        if (payload != null) {
            val protoMsg = toProtoMessage(payload)
            payloadBytes = protoMsg.toByteArray()
            require(payloadBytes.size <= MAX_PAYLOAD_BYTES) { "Listen Together payload exceeds the size limit" }

            if (compressionEnabled && payloadBytes.size > COMPRESSION_THRESHOLD) {
                val compressedBytes = compressData(payloadBytes)
                if (compressedBytes.size < payloadBytes.size) {
                    payloadBytes = compressedBytes
                    compressed = true
                }
            }
        }

        val envelope =
            Listentogether.Envelope
                .newBuilder()
                .setType(msgType)
                .setPayload(
                    com.google.protobuf.ByteString
                        .copyFrom(payloadBytes),
                ).setCompressed(compressed)
                .setRequestId(requestId)
                .build()

        return envelope.toByteArray().also {
            require(it.size <= MAX_ENVELOPE_BYTES) { "Listen Together envelope exceeds the size limit" }
        }
    }

    private fun decodeProtobuf(data: ByteArray): ListenTogetherEnvelope {
        require(data.size <= MAX_ENVELOPE_BYTES) { "Listen Together envelope exceeds the size limit" }
        val envelope = Listentogether.Envelope.parseFrom(data)
        require(envelope.type in knownMessageTypes) { "Unsupported Listen Together message type: ${envelope.type}" }

        var payloadBytes = envelope.payload.toByteArray()

        if (envelope.compressed) {
            payloadBytes = decompressData(payloadBytes)
        }
        require(payloadBytes.size <= MAX_PAYLOAD_BYTES) { "Listen Together payload exceeds the size limit" }

        return ListenTogetherEnvelope(envelope.type, payloadBytes, envelope.requestId)
    }

    private fun compressData(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzip ->
            gzip.write(data)
        }
        return outputStream.toByteArray()
    }

    private fun decompressData(data: ByteArray): ByteArray {
        try {
            return GZIPInputStream(ByteArrayInputStream(data)).use { gzip ->
                val output = ByteArrayOutputStream(minOf(data.size * 4, MAX_PAYLOAD_BYTES))
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = gzip.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= MAX_PAYLOAD_BYTES) {
                        "Compressed Listen Together payload exceeds the size limit"
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid compressed Listen Together payload", e)
        }
    }

    private fun toProtoMessage(payload: Any): com.google.protobuf.MessageLite =
        when (payload) {
            is CreateRoomPayload -> {
                Listentogether.CreateRoomPayload
                    .newBuilder()
                    .setUsername(payload.username)
                    .build()
            }

            is JoinRoomPayload -> {
                Listentogether.JoinRoomPayload
                    .newBuilder()
                    .setRoomCode(payload.roomCode)
                    .setUsername(payload.username)
                    .build()
            }

            is ApproveJoinPayload -> {
                Listentogether.ApproveJoinPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .build()
            }

            is RejectJoinPayload -> {
                Listentogether.RejectJoinPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setReason(payload.reason ?: "")
                    .build()
            }

            is PlaybackActionPayload -> {
                val builder =
                    Listentogether.PlaybackActionPayload
                        .newBuilder()
                        .setAction(payload.action)
                        .setPosition(payload.position ?: 0)
                        .setInsertNext(payload.insertNext ?: false)
                        .setVolume(payload.volume ?: 1f)
                        .setServerTime(payload.serverTime ?: 0)
                        .setRevision(payload.revision)
                        .setCapturedAtServerTime(payload.capturedAtServerTime ?: 0)
                        .setReplaceQueue(payload.replaceQueue)
                        .setHostSequence(payload.hostSequence)

                payload.trackId?.let { builder.setTrackId(it) }
                payload.originUserId?.let { builder.setOriginUserId(it) }
                payload.trackInfo?.let { builder.setTrackInfo(trackInfoToProto(it)) }
                payload.queueTitle?.let { builder.setQueueTitle(it) }
                payload.queue?.forEach { track ->
                    builder.addQueue(trackInfoToProto(track))
                }

                builder.build()
            }

            is PingPayload -> {
                Listentogether.PingPayload
                    .newBuilder()
                    .setClientTime(payload.clientTime)
                    .setSequence(payload.sequence)
                    .setHasPosition(payload.hasPosition)
                    .setTrackId(payload.trackId ?: "")
                    .setPosition(payload.position)
                    .setPositionCapturedAtServerTime(payload.positionCapturedAtServerTime)
                    .setHasStateHash(payload.hasStateHash)
                    .setStateHash(payload.stateHash.toInt())
                    .build()
            }

            is BufferReadyPayload -> {
                Listentogether.BufferReadyPayload
                    .newBuilder()
                    .setTrackId(payload.trackId)
                    .setTrackRevision(payload.trackRevision)
                    .build()
            }

            is KickUserPayload -> {
                Listentogether.KickUserPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setReason(payload.reason ?: "")
                    .build()
            }

            is SuggestTrackPayload -> {
                val builder = Listentogether.SuggestTrackPayload.newBuilder()
                payload.trackInfo.let { builder.setTrackInfo(trackInfoToProto(it)) }
                builder.build()
            }

            is ApproveSuggestionPayload -> {
                Listentogether.ApproveSuggestionPayload
                    .newBuilder()
                    .setSuggestionId(payload.suggestionId)
                    .build()
            }

            is RejectSuggestionPayload -> {
                Listentogether.RejectSuggestionPayload
                    .newBuilder()
                    .setSuggestionId(payload.suggestionId)
                    .setReason(payload.reason ?: "")
                    .build()
            }

            is ReconnectPayload -> {
                Listentogether.ReconnectPayload
                    .newBuilder()
                    .setSessionToken(payload.sessionToken)
                    .build()
            }

            is ClientCapabilities -> {
                Listentogether.ClientCapabilities
                    .newBuilder()
                    .setSupportsProtobuf(payload.supportsProtobuf)
                    .setSupportsCompression(payload.supportsCompression)
                    .setClientVersion(payload.clientVersion)
                    .addAllFeatures(payload.features)
                    .build()
            }

            is ServerCapabilities -> {
                Listentogether.ServerCapabilities
                    .newBuilder()
                    .setSupportsProtobuf(payload.supportsProtobuf)
                    .setSupportsCompression(payload.supportsCompression)
                    .setServerVersion(payload.serverVersion)
                    .addAllFeatures(payload.features)
                    .setCanHost(payload.canHost)
                    .build()
            }

            is ServerRestartingPayload -> {
                Listentogether.ServerRestartingPayload
                    .newBuilder()
                    .setEtaMs(payload.etaMs)
                    .setReason(payload.reason ?: "")
                    .build()
            }

            is SuggestionSubmittedPayload -> {
                Listentogether.SuggestionSubmittedPayload
                    .newBuilder()
                    .setSuggestionId(payload.suggestionId)
                    .setTrackInfo(trackInfoToProto(payload.trackInfo))
                    .build()
            }

            is TransferHostPayload -> {
                Listentogether.TransferHostPayload
                    .newBuilder()
                    .setNewHostId(payload.newHostId)
                    .build()
            }

            is RoomCreatedPayload -> {
                Listentogether.RoomCreatedPayload
                    .newBuilder()
                    .setRoomCode(payload.roomCode)
                    .setUserId(payload.userId)
                    .setSessionToken(payload.sessionToken)
                    .build()
            }

            is JoinRequestPayload -> {
                Listentogether.JoinRequestPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setUsername(payload.username)
                    .build()
            }

            is JoinApprovedPayload -> {
                Listentogether.JoinApprovedPayload
                    .newBuilder()
                    .setRoomCode(payload.roomCode)
                    .setUserId(payload.userId)
                    .setSessionToken(payload.sessionToken)
                    .setState(roomStateToProto(payload.state))
                    .build()
            }

            is JoinRejectedPayload -> {
                Listentogether.JoinRejectedPayload
                    .newBuilder()
                    .setReason(payload.reason)
                    .build()
            }

            is UserJoinedPayload -> {
                Listentogether.UserJoinedPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setUsername(payload.username)
                    .build()
            }

            is UserLeftPayload -> {
                Listentogether.UserLeftPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setUsername(payload.username)
                    .build()
            }

            is BufferWaitPayload -> {
                Listentogether.BufferWaitPayload
                    .newBuilder()
                    .setTrackId(payload.trackId)
                    .addAllWaitingFor(payload.waitingFor)
                    .build()
            }

            is BufferCompletePayload -> {
                Listentogether.BufferCompletePayload
                    .newBuilder()
                    .setTrackId(payload.trackId)
                    .build()
            }

            is ErrorPayload -> {
                Listentogether.ErrorPayload
                    .newBuilder()
                    .setCode(payload.code)
                    .setMessage(payload.message)
                    .build()
            }

            is PongPayload -> {
                Listentogether.PongPayload
                    .newBuilder()
                    .setClientTime(payload.clientTime)
                    .setServerReceiveTime(payload.serverReceiveTime)
                    .setServerSendTime(payload.serverSendTime)
                    .setSequence(payload.sequence)
                    .setDriftValid(payload.driftValid)
                    .setDriftMs(payload.driftMs)
                    .setStateMismatch(payload.stateMismatch)
                    .build()
            }

            is HostChangedPayload -> {
                Listentogether.HostChangedPayload
                    .newBuilder()
                    .setNewHostId(payload.newHostId)
                    .setNewHostName(payload.newHostName)
                    .build()
            }

            is KickedPayload -> {
                Listentogether.KickedPayload
                    .newBuilder()
                    .setReason(payload.reason)
                    .build()
            }

            is SyncStatePayload -> {
                val builder =
                    Listentogether.SyncStatePayload
                        .newBuilder()
                        .setIsPlaying(payload.isPlaying)
                        .setPosition(payload.position)
                        .setLastUpdate(payload.lastUpdate)
                        .setRevision(payload.revision)
                payload.currentTrack?.let { builder.setCurrentTrack(trackInfoToProto(it)) }
                payload.queue?.forEach { builder.addQueue(trackInfoToProto(it)) }
                payload.volume?.let { builder.setVolume(it) }
                payload.queueTitle?.let { builder.setQueueTitle(it) }
                builder.build()
            }

            is ReconnectedPayload -> {
                Listentogether.ReconnectedPayload
                    .newBuilder()
                    .setRoomCode(payload.roomCode)
                    .setUserId(payload.userId)
                    .setState(roomStateToProto(payload.state))
                    .setIsHost(payload.isHost)
                    .build()
            }

            is UserReconnectedPayload -> {
                Listentogether.UserReconnectedPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setUsername(payload.username)
                    .build()
            }

            is UserDisconnectedPayload -> {
                Listentogether.UserDisconnectedPayload
                    .newBuilder()
                    .setUserId(payload.userId)
                    .setUsername(payload.username)
                    .build()
            }

            is SuggestionReceivedPayload -> {
                Listentogether.SuggestionReceivedPayload
                    .newBuilder()
                    .setSuggestionId(payload.suggestionId)
                    .setFromUserId(payload.fromUserId)
                    .setFromUsername(payload.fromUsername)
                    .setTrackInfo(trackInfoToProto(payload.trackInfo))
                    .build()
            }

            is SuggestionApprovedPayload -> {
                Listentogether.SuggestionApprovedPayload
                    .newBuilder()
                    .setSuggestionId(payload.suggestionId)
                    .setTrackInfo(trackInfoToProto(payload.trackInfo))
                    .build()
            }

            is SuggestionRejectedPayload -> {
                Listentogether.SuggestionRejectedPayload
                    .newBuilder()
                    .setSuggestionId(payload.suggestionId)
                    .setReason(payload.reason.orEmpty())
                    .build()
            }

            is LinkRegisterPayload -> {
                Listentogether.LinkRegisterPayload
                    .newBuilder()
                    .setDeviceName(payload.deviceName)
                    .setDeviceType(payload.deviceType)
                    .setAppVersion(payload.appVersion)
                    .build()
            }

            is LinkPairPayload -> {
                Listentogether.LinkPairPayload
                    .newBuilder()
                    .setPairingCode(payload.pairingCode)
                    .setDeviceName(payload.deviceName)
                    .setDeviceType(payload.deviceType)
                    .setAppVersion(payload.appVersion)
                    .build()
            }

            is LinkCommandPayload -> {
                val builder =
                    Listentogether.LinkCommandPayload
                        .newBuilder()
                        .setAction(payload.action)
                        .setPosition(payload.position ?: 0)
                        .setInsertNext(payload.insertNext ?: false)
                        .setVolume(payload.volume ?: 0f)
                        .setCapturedAtServerTime(payload.capturedAtServerTime ?: 0)
                payload.trackId?.let { builder.setTrackId(it) }
                payload.trackInfo?.let { builder.setTrackInfo(trackInfoToProto(it)) }
                payload.queueTitle?.let { builder.setQueueTitle(it) }
                payload.queue?.forEach { builder.addQueue(trackInfoToProto(it)) }
                payload.fromDeviceId?.let { builder.setFromDeviceId(it) }
                payload.fromDeviceName?.let { builder.setFromDeviceName(it) }
                builder.build()
            }

            is LinkStatePayload -> linkStateToProto(payload)

            is LinkHandoffPayload -> {
                Listentogether.LinkHandoffPayload
                    .newBuilder()
                    .setTargetDeviceId(payload.targetDeviceId)
                    .build()
            }

            is LinkSessionPayload -> {
                val builder =
                    Listentogether.LinkSessionPayload
                        .newBuilder()
                        .setLinkId(payload.linkId)
                        .setDeviceId(payload.deviceId)
                        .setPairingCode(payload.pairingCode)
                        .setPlayerDeviceId(payload.playerDeviceId)
                payload.devices.forEach { builder.addDevices(linkDeviceInfoToProto(it)) }
                payload.state?.let { builder.setState(linkStateToProto(it)) }
                builder.build()
            }

            is LinkDeviceJoinedPayload -> {
                Listentogether.LinkDeviceJoinedPayload
                    .newBuilder()
                    .setDevice(linkDeviceInfoToProto(payload.device))
                    .build()
            }

            is LinkDeviceLeftPayload -> {
                Listentogether.LinkDeviceLeftPayload
                    .newBuilder()
                    .setDeviceId(payload.deviceId)
                    .setWasPlayer(payload.wasPlayer)
                    .build()
            }

            is LinkHandoffStartPayload -> {
                val builder = Listentogether.LinkHandoffStartPayload.newBuilder()
                payload.state?.let { builder.setState(linkStateToProto(it)) }
                payload.fromDeviceId?.let { builder.setFromDeviceId(it) }
                builder.build()
            }

            is LinkStopPayload -> {
                val builder = Listentogether.LinkStopPayload.newBuilder()
                payload.state?.let { builder.setState(linkStateToProto(it)) }
                payload.toDeviceId?.let { builder.setToDeviceId(it) }
                builder.build()
            }

            is LinkPlayerChangedPayload -> {
                Listentogether.LinkPlayerChangedPayload
                    .newBuilder()
                    .setNewPlayerDeviceId(payload.newPlayerDeviceId)
                    .setNewPlayerDeviceName(payload.newPlayerDeviceName)
                    .build()
            }

            else -> {
                throw IllegalArgumentException("Unsupported payload type: ${payload::class.simpleName}")
            }
        }

    override fun decodePayload(
        msgType: String,
        payloadBytes: ByteArray,
    ): Any? {
        require(msgType in knownMessageTypes) { "Unsupported Listen Together message type: $msgType" }
        require(payloadBytes.size <= MAX_PAYLOAD_BYTES) { "Listen Together payload exceeds the size limit" }
        if (payloadBytes.isEmpty() && msgType in payloadlessMessageTypes) return null

        return decodeProtobufPayload(msgType, payloadBytes)
    }

    private fun decodeProtobufPayload(
        msgType: String,
        payloadBytes: ByteArray,
    ): Any =
        when (msgType) {
            MessageTypes.CREATE_ROOM -> {
                val pb = Listentogether.CreateRoomPayload.parseFrom(payloadBytes)
                CreateRoomPayload(pb.username)
            }

            MessageTypes.JOIN_ROOM -> {
                val pb = Listentogether.JoinRoomPayload.parseFrom(payloadBytes)
                JoinRoomPayload(pb.roomCode, pb.username)
            }

            MessageTypes.APPROVE_JOIN -> {
                val pb = Listentogether.ApproveJoinPayload.parseFrom(payloadBytes)
                ApproveJoinPayload(pb.userId)
            }

            MessageTypes.REJECT_JOIN -> {
                val pb = Listentogether.RejectJoinPayload.parseFrom(payloadBytes)
                RejectJoinPayload(pb.userId, pb.reason.takeIf { it.isNotEmpty() })
            }

            MessageTypes.PLAYBACK_ACTION -> {
                decodePlaybackAction(payloadBytes)
            }

            MessageTypes.BUFFER_READY -> {
                val pb = Listentogether.BufferReadyPayload.parseFrom(payloadBytes)
                BufferReadyPayload(pb.trackId, pb.trackRevision)
            }

            MessageTypes.KICK_USER -> {
                val pb = Listentogether.KickUserPayload.parseFrom(payloadBytes)
                KickUserPayload(pb.userId, pb.reason.takeIf { it.isNotEmpty() })
            }

            MessageTypes.TRANSFER_HOST -> {
                val pb = Listentogether.TransferHostPayload.parseFrom(payloadBytes)
                TransferHostPayload(pb.newHostId)
            }

            MessageTypes.PING -> {
                val pb = Listentogether.PingPayload.parseFrom(payloadBytes)
                PingPayload(
                    clientTime = pb.clientTime,
                    sequence = pb.sequence,
                    hasPosition = pb.hasPosition,
                    trackId = pb.trackId.takeIf { it.isNotEmpty() },
                    position = pb.position,
                    positionCapturedAtServerTime = pb.positionCapturedAtServerTime,
                    hasStateHash = pb.hasStateHash,
                    stateHash = pb.stateHash.toLong() and 0xFFFFFFFFL,
                )
            }

            MessageTypes.RECONNECT -> {
                val pb = Listentogether.ReconnectPayload.parseFrom(payloadBytes)
                ReconnectPayload(pb.sessionToken)
            }

            MessageTypes.SUGGEST_TRACK -> {
                val pb = Listentogether.SuggestTrackPayload.parseFrom(payloadBytes)
                SuggestTrackPayload(protoToTrackInfo(pb.trackInfo))
            }

            MessageTypes.APPROVE_SUGGESTION -> {
                val pb = Listentogether.ApproveSuggestionPayload.parseFrom(payloadBytes)
                ApproveSuggestionPayload(pb.suggestionId)
            }

            MessageTypes.REJECT_SUGGESTION -> {
                val pb = Listentogether.RejectSuggestionPayload.parseFrom(payloadBytes)
                RejectSuggestionPayload(pb.suggestionId, pb.reason.takeIf { it.isNotEmpty() })
            }

            MessageTypes.ROOM_CREATED -> {
                val pb = Listentogether.RoomCreatedPayload.parseFrom(payloadBytes)
                require(pb.roomCode.isNotBlank() && pb.userId.isNotBlank() && pb.sessionToken.isNotBlank()) { "Missing room identifiers" }
                RoomCreatedPayload(pb.roomCode, pb.userId, pb.sessionToken)
            }

            MessageTypes.JOIN_REQUEST -> {
                val pb = Listentogether.JoinRequestPayload.parseFrom(payloadBytes)
                JoinRequestPayload(pb.userId, pb.username)
            }

            MessageTypes.JOIN_APPROVED -> {
                val pb = Listentogether.JoinApprovedPayload.parseFrom(payloadBytes)
                require(pb.hasState()) { "Missing room state" }
                require(pb.sessionToken.isNotBlank()) { "Missing room session token" }
                validateRoomResponse(pb.roomCode, pb.userId, protoToRoomState(pb.state))
                JoinApprovedPayload(
                    pb.roomCode,
                    pb.userId,
                    pb.sessionToken,
                    protoToRoomState(pb.state),
                )
            }

            MessageTypes.JOIN_REJECTED -> {
                val pb = Listentogether.JoinRejectedPayload.parseFrom(payloadBytes)
                JoinRejectedPayload(pb.reason)
            }

            MessageTypes.USER_JOINED -> {
                val pb = Listentogether.UserJoinedPayload.parseFrom(payloadBytes)
                UserJoinedPayload(pb.userId, pb.username)
            }

            MessageTypes.USER_LEFT -> {
                val pb = Listentogether.UserLeftPayload.parseFrom(payloadBytes)
                UserLeftPayload(pb.userId, pb.username)
            }

            MessageTypes.SYNC_PLAYBACK -> {
                decodePlaybackAction(payloadBytes)
            }

            MessageTypes.PONG -> {
                val pb = Listentogether.PongPayload.parseFrom(payloadBytes)
                PongPayload(
                    pb.clientTime,
                    pb.serverReceiveTime,
                    pb.serverSendTime,
                    pb.sequence,
                    pb.driftValid,
                    pb.driftMs,
                    pb.stateMismatch,
                )
            }

            MessageTypes.BUFFER_WAIT -> {
                val pb = Listentogether.BufferWaitPayload.parseFrom(payloadBytes)
                BufferWaitPayload(pb.trackId, pb.waitingForList)
            }

            MessageTypes.BUFFER_COMPLETE -> {
                val pb = Listentogether.BufferCompletePayload.parseFrom(payloadBytes)
                BufferCompletePayload(pb.trackId)
            }

            MessageTypes.ERROR -> {
                val pb = Listentogether.ErrorPayload.parseFrom(payloadBytes)
                ErrorPayload(pb.code, pb.message)
            }

            MessageTypes.HOST_CHANGED -> {
                val pb = Listentogether.HostChangedPayload.parseFrom(payloadBytes)
                HostChangedPayload(pb.newHostId, pb.newHostName)
            }

            MessageTypes.KICKED -> {
                val pb = Listentogether.KickedPayload.parseFrom(payloadBytes)
                KickedPayload(pb.reason)
            }

            MessageTypes.SYNC_STATE -> {
                val pb = Listentogether.SyncStatePayload.parseFrom(payloadBytes)
                SyncStatePayload(
                    currentTrack = if (pb.hasCurrentTrack()) protoToTrackInfo(pb.currentTrack) else null,
                    isPlaying = pb.isPlaying,
                    position = pb.position,
                    lastUpdate = pb.lastUpdate,
                    queue = pb.queueList.map { protoToTrackInfo(it) },
                    volume = pb.volume,
                    revision = pb.revision,
                    queueTitle = pb.queueTitle.takeIf { it.isNotEmpty() },
                )
            }

            MessageTypes.RECONNECTED -> {
                val pb = Listentogether.ReconnectedPayload.parseFrom(payloadBytes)
                require(pb.hasState()) { "Missing room state" }
                validateRoomResponse(pb.roomCode, pb.userId, protoToRoomState(pb.state))
                ReconnectedPayload(
                    pb.roomCode,
                    pb.userId,
                    protoToRoomState(pb.state),
                    pb.isHost,
                )
            }

            MessageTypes.USER_RECONNECTED -> {
                val pb = Listentogether.UserReconnectedPayload.parseFrom(payloadBytes)
                UserReconnectedPayload(pb.userId, pb.username)
            }

            MessageTypes.USER_DISCONNECTED -> {
                val pb = Listentogether.UserDisconnectedPayload.parseFrom(payloadBytes)
                UserDisconnectedPayload(pb.userId, pb.username)
            }

            MessageTypes.SUGGESTION_RECEIVED -> {
                val pb = Listentogether.SuggestionReceivedPayload.parseFrom(payloadBytes)
                SuggestionReceivedPayload(
                    pb.suggestionId,
                    pb.fromUserId,
                    pb.fromUsername,
                    protoToTrackInfo(pb.trackInfo),
                )
            }

            MessageTypes.SUGGESTION_APPROVED -> {
                val pb = Listentogether.SuggestionApprovedPayload.parseFrom(payloadBytes)
                SuggestionApprovedPayload(
                    pb.suggestionId,
                    protoToTrackInfo(pb.trackInfo),
                )
            }

            MessageTypes.SUGGESTION_REJECTED -> {
                val pb = Listentogether.SuggestionRejectedPayload.parseFrom(payloadBytes)
                SuggestionRejectedPayload(pb.suggestionId, pb.reason.takeIf { it.isNotEmpty() })
            }

            MessageTypes.CLIENT_CAPABILITIES -> {
                val pb = Listentogether.ClientCapabilities.parseFrom(payloadBytes)
                ClientCapabilities(pb.supportsProtobuf, pb.supportsCompression, pb.clientVersion, pb.featuresList)
            }

            MessageTypes.SERVER_CAPABILITIES -> {
                val pb = Listentogether.ServerCapabilities.parseFrom(payloadBytes)
                ServerCapabilities(pb.supportsProtobuf, pb.supportsCompression, pb.serverVersion, pb.featuresList, pb.canHost)
            }

            MessageTypes.SERVER_RESTARTING -> {
                val pb = Listentogether.ServerRestartingPayload.parseFrom(payloadBytes)
                ServerRestartingPayload(pb.etaMs, pb.reason.takeIf { it.isNotEmpty() })
            }

            MessageTypes.SUGGESTION_SUBMITTED -> {
                val pb = Listentogether.SuggestionSubmittedPayload.parseFrom(payloadBytes)
                SuggestionSubmittedPayload(pb.suggestionId, protoToTrackInfo(pb.trackInfo))
            }

            LinkMessageTypes.LINK_REGISTER -> {
                val pb = Listentogether.LinkRegisterPayload.parseFrom(payloadBytes)
                LinkRegisterPayload(pb.deviceName, pb.deviceType, pb.appVersion)
            }

            LinkMessageTypes.LINK_PAIR -> {
                val pb = Listentogether.LinkPairPayload.parseFrom(payloadBytes)
                LinkPairPayload(pb.pairingCode, pb.deviceName, pb.deviceType, pb.appVersion)
            }

            LinkMessageTypes.LINK_COMMAND -> {
                val pb = Listentogether.LinkCommandPayload.parseFrom(payloadBytes)
                LinkCommandPayload(
                    action = pb.action,
                    trackId = pb.trackId.takeIf { it.isNotEmpty() },
                    position = pb.position,
                    trackInfo = if (pb.hasTrackInfo()) protoToTrackInfo(pb.trackInfo) else null,
                    insertNext = pb.insertNext.takeIf { it },
                    queue = pb.queueList.map(::protoToTrackInfo),
                    queueTitle = pb.queueTitle.takeIf { it.isNotEmpty() },
                    volume = pb.volume.takeIf { pb.action == PlaybackActions.SET_VOLUME },
                    capturedAtServerTime = pb.capturedAtServerTime.takeIf { it > 0 },
                    fromDeviceId = pb.fromDeviceId.takeIf { it.isNotEmpty() },
                    fromDeviceName = pb.fromDeviceName.takeIf { it.isNotEmpty() },
                )
            }

            LinkMessageTypes.LINK_STATE -> {
                protoToLinkState(Listentogether.LinkStatePayload.parseFrom(payloadBytes))
            }

            LinkMessageTypes.LINK_HANDOFF -> {
                val pb = Listentogether.LinkHandoffPayload.parseFrom(payloadBytes)
                LinkHandoffPayload(pb.targetDeviceId)
            }

            LinkMessageTypes.LINK_REGISTERED, LinkMessageTypes.LINK_PAIRED -> {
                val pb = Listentogether.LinkSessionPayload.parseFrom(payloadBytes)
                LinkSessionPayload(
                    linkId = pb.linkId,
                    deviceId = pb.deviceId,
                    pairingCode = pb.pairingCode,
                    devices = pb.devicesList.map(::protoToLinkDevice),
                    playerDeviceId = pb.playerDeviceId,
                    state = if (pb.hasState()) protoToLinkState(pb.state) else null,
                )
            }

            LinkMessageTypes.LINK_DEVICE_JOINED -> {
                val pb = Listentogether.LinkDeviceJoinedPayload.parseFrom(payloadBytes)
                LinkDeviceJoinedPayload(protoToLinkDevice(pb.device))
            }

            LinkMessageTypes.LINK_DEVICE_LEFT -> {
                val pb = Listentogether.LinkDeviceLeftPayload.parseFrom(payloadBytes)
                LinkDeviceLeftPayload(pb.deviceId, pb.wasPlayer)
            }

            LinkMessageTypes.LINK_HANDOFF_START -> {
                val pb = Listentogether.LinkHandoffStartPayload.parseFrom(payloadBytes)
                LinkHandoffStartPayload(
                    state = if (pb.hasState()) protoToLinkState(pb.state) else null,
                    fromDeviceId = pb.fromDeviceId.takeIf { it.isNotEmpty() },
                )
            }

            LinkMessageTypes.LINK_STOP -> {
                val pb = Listentogether.LinkStopPayload.parseFrom(payloadBytes)
                LinkStopPayload(
                    state = if (pb.hasState()) protoToLinkState(pb.state) else null,
                    toDeviceId = pb.toDeviceId.takeIf { it.isNotEmpty() },
                )
            }

            LinkMessageTypes.LINK_PLAYER_CHANGED -> {
                val pb = Listentogether.LinkPlayerChangedPayload.parseFrom(payloadBytes)
                LinkPlayerChangedPayload(pb.newPlayerDeviceId, pb.newPlayerDeviceName)
            }

            else -> {
                throw IllegalArgumentException("Unsupported Listen Together message type: $msgType")
            }
        }

    private fun decodePlaybackAction(payloadBytes: ByteArray): PlaybackActionPayload {
        val pb = Listentogether.PlaybackActionPayload.parseFrom(payloadBytes)
        return PlaybackActionPayload(
            action = pb.action,
            trackId = pb.trackId.takeIf { it.isNotEmpty() },
            position = pb.position,
            trackInfo = if (pb.hasTrackInfo()) protoToTrackInfo(pb.trackInfo) else null,
            insertNext = pb.insertNext.takeIf { it },
            queue = pb.queueList.map(::protoToTrackInfo),
            queueTitle = pb.queueTitle.takeIf { it.isNotEmpty() },
            volume = pb.volume,
            serverTime = pb.serverTime.takeIf { it > 0 },
            revision = pb.revision,
            capturedAtServerTime = pb.capturedAtServerTime.takeIf { it > 0 },
            replaceQueue = pb.replaceQueue,
            originUserId = pb.originUserId.takeIf { it.isNotEmpty() },
            hostSequence = pb.hostSequence,
        )
    }

    private fun trackInfoToProto(track: TrackInfo): Listentogether.TrackInfo =
        Listentogether.TrackInfo
            .newBuilder()
            .setId(track.id)
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbum(track.album ?: "")
            .setDuration(track.duration)
            .setThumbnail(track.thumbnail ?: "")
            .setSuggestedBy(track.suggestedBy ?: "")
            .build()

    private fun roomStateToProto(state: RoomState): Listentogether.RoomState {
        val builder =
            Listentogether.RoomState
                .newBuilder()
                .setRoomCode(state.roomCode)
                .setHostId(state.hostId)
                .addAllUsers(state.users.map(::userInfoToProto))
                .setIsPlaying(state.isPlaying)
                .setPosition(state.position)
                .setLastUpdate(state.lastUpdate)
                .setVolume(state.volume)
                .addAllQueue(state.queue.map(::trackInfoToProto))
                .setRevision(state.revision)
                .setQueueTitle(state.queueTitle ?: "")
        state.currentTrack?.let { builder.setCurrentTrack(trackInfoToProto(it)) }
        return builder.build()
    }

    private fun userInfoToProto(user: UserInfo): Listentogether.UserInfo =
        Listentogether.UserInfo
            .newBuilder()
            .setUserId(user.userId)
            .setUsername(user.username)
            .setIsHost(user.isHost)
            .setIsConnected(user.isConnected)
            .build()

    private fun protoToTrackInfo(proto: Listentogether.TrackInfo): TrackInfo =
        TrackInfo(
            id = proto.id,
            title = proto.title,
            artist = proto.artist,
            album = proto.album.takeIf { it.isNotEmpty() },
            duration = proto.duration,
            thumbnail = proto.thumbnail.takeIf { it.isNotEmpty() },
            suggestedBy = proto.suggestedBy.takeIf { it.isNotEmpty() },
        )

    private fun linkDeviceInfoToProto(device: LinkDeviceInfo): Listentogether.LinkDeviceInfo =
        Listentogether.LinkDeviceInfo
            .newBuilder()
            .setDeviceId(device.deviceId)
            .setDeviceName(device.deviceName)
            .setDeviceType(device.deviceType)
            .setAppVersion(device.appVersion)
            .setIsPlayer(device.isPlayer)
            .build()

    private fun protoToLinkDevice(proto: Listentogether.LinkDeviceInfo): LinkDeviceInfo =
        LinkDeviceInfo(
            deviceId = proto.deviceId,
            deviceName = proto.deviceName,
            deviceType = proto.deviceType.ifEmpty { LinkDeviceTypes.OTHER },
            appVersion = proto.appVersion,
            isPlayer = proto.isPlayer,
        )

    private fun linkStateToProto(state: LinkStatePayload): Listentogether.LinkStatePayload {
        val builder =
            Listentogether.LinkStatePayload
                .newBuilder()
                .setIsPlaying(state.isPlaying)
                .setPosition(state.position)
                .setLastUpdate(state.lastUpdate)
                .setVolume(state.volume)
                .setRevision(state.revision)
        state.currentTrack?.let { builder.setCurrentTrack(trackInfoToProto(it)) }
        state.queue.forEach { builder.addQueue(trackInfoToProto(it)) }
        return builder.build()
    }

    private fun protoToLinkState(proto: Listentogether.LinkStatePayload): LinkStatePayload =
        LinkStatePayload(
            currentTrack = if (proto.hasCurrentTrack()) protoToTrackInfo(proto.currentTrack) else null,
            isPlaying = proto.isPlaying,
            position = proto.position,
            lastUpdate = proto.lastUpdate,
            queue = proto.queueList.map(::protoToTrackInfo),
            volume = proto.volume,
            revision = proto.revision,
        )

    private fun protoToUserInfo(proto: Listentogether.UserInfo): UserInfo =
        UserInfo(
            userId = proto.userId,
            username = proto.username,
            isHost = proto.isHost,
            isConnected = proto.isConnected,
        )

    private fun protoToRoomState(proto: Listentogether.RoomState): RoomState =
        RoomState(
            roomCode = proto.roomCode,
            hostId = proto.hostId,
            users = proto.usersList.map { protoToUserInfo(it) },
            currentTrack = if (proto.hasCurrentTrack()) protoToTrackInfo(proto.currentTrack) else null,
            isPlaying = proto.isPlaying,
            position = proto.position,
            lastUpdate = proto.lastUpdate,
            volume = proto.volume,
            queue = proto.queueList.map { protoToTrackInfo(it) },
            revision = proto.revision,
            queueTitle = proto.queueTitle.takeIf { it.isNotEmpty() },
        )
}
