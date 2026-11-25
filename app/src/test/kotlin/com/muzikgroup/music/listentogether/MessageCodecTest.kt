package com.muzikgroup.music.listentogether

import com.google.protobuf.ByteString
import com.muzikgroup.music.listentogether.proto.Listentogether
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageCodecTest {
    private val codec = MessageCodec(compressionEnabled = true)

    @Test
    fun `playback timing fields survive a protobuf round trip`() {
        val action =
            PlaybackActionPayload(
                action = PlaybackActions.PLAY,
                trackId = "track",
                position = 1_234L,
                serverTime = 9_000L,
                revision = 12L,
                capturedAtServerTime = 8_950L,
            )

        val (type, payload) = codec.decode(codec.encode(MessageTypes.PLAYBACK_ACTION, action))
        val decoded = codec.decodePayload(MessageTypes.SYNC_PLAYBACK, payload) as PlaybackActionPayload

        assertEquals(MessageTypes.PLAYBACK_ACTION, type)
        assertEquals(action.action, decoded.action)
        assertEquals(action.trackId, decoded.trackId)
        assertEquals(action.position, decoded.position)
        assertEquals(action.serverTime, decoded.serverTime)
        assertEquals(action.revision, decoded.revision)
        assertEquals(action.capturedAtServerTime, decoded.capturedAtServerTime)
    }

    @Test
    fun `timestamped ping is encoded and pong is decoded`() {
        val ping = PingPayload(clientTime = 1_000L, sequence = 3L)
        val (_, pingBytes) = codec.decode(codec.encode(MessageTypes.PING, ping))
        val encodedPing = Listentogether.PingPayload.parseFrom(pingBytes)
        assertEquals(1_000L, encodedPing.clientTime)
        assertEquals(3L, encodedPing.sequence)

        val pong =
            Listentogether.PongPayload
                .newBuilder()
                .setClientTime(1_000L)
                .setServerReceiveTime(10_000L)
                .setServerSendTime(10_001L)
                .setSequence(3L)
                .build()
        val envelope =
            Listentogether.Envelope
                .newBuilder()
                .setType(MessageTypes.PONG)
                .setPayload(ByteString.copyFrom(pong.toByteArray()))
                .build()
        val (type, pongBytes) = codec.decode(envelope.toByteArray())
        val decoded = codec.decodePayload(type, pongBytes) as PongPayload

        assertEquals(PongPayload(1_000L, 10_000L, 10_001L, 3L), decoded)
        assertTrue(decoded.serverSendTime >= decoded.serverReceiveTime)
    }
}

@Test
fun `muzik link session payload survives a protobuf round trip`() {
    val session =
        LinkSessionPayload(
            linkId = "link_123",
            deviceId = "phone",
            pairingCode = "A2C4E6",
            devices =
                listOf(
                    LinkDeviceInfo(
                        deviceId = "desktop",
                        deviceName = "Study PC",
                        deviceType = LinkDeviceTypes.DESKTOP,
                        appVersion = "8.3.0",
                        isPlayer = true,
                    ),
                    LinkDeviceInfo(
                        deviceId = "phone",
                        deviceName = "Pixel 9",
                        deviceType = LinkDeviceTypes.ANDROID,
                        isPlayer = false,
                    ),
                ),
            playerDeviceId = "desktop",
            state =
                LinkStatePayload(
                    currentTrack = TrackInfo(id = "abc", title = "Song", artist = "Artist", duration = 200_000L),
                    isPlaying = true,
                    position = 45_000L,
                    lastUpdate = 77_000L,
                    queue = listOf(TrackInfo(id = "def", title = "Next", artist = "Artist", duration = 180_000L)),
                    volume = 0.8f,
                ),
        )

    val (type, payload) = codec.decode(codec.encode(LinkMessageTypes.LINK_PAIRED, session))
    val decoded = codec.decodePayload(type, payload) as LinkSessionPayload

    assertEquals(session.linkId, decoded.linkId)
    assertEquals(session.deviceId, decoded.deviceId)
    assertEquals(session.pairingCode, decoded.pairingCode)
    assertEquals(session.playerDeviceId, decoded.playerDeviceId)
    assertEquals(2, decoded.devices.size)
    assertEquals("desktop", decoded.devices[0].deviceId)
    assertEquals(LinkDeviceTypes.DESKTOP, decoded.devices[0].deviceType)
    assertTrue(decoded.devices[0].isPlayer)
    assertEquals(true, decoded.state?.isPlaying)
    assertEquals(45_000L, decoded.state?.position)
    assertEquals("def", decoded.state?.queue?.firstOrNull()?.id)
    assertEquals(0.8f, decoded.state?.volume)
}

@Test
fun `muzik link command and state payloads survive protobuf round trips`() {
    val command =
        LinkCommandPayload(
            action = PlaybackActions.SEEK,
            trackId = "abc",
            position = 12_345L,
            volume = 0.4f,
            capturedAtServerTime = 55_000L,
            fromDeviceId = "phone",
            fromDeviceName = "Pixel 9",
        )
    val commandBytes = codec.encode(LinkMessageTypes.LINK_COMMAND, command)
    val (commandType, commandPayload) = codec.decode(commandBytes)
    val decodedCommand = codec.decodePayload(commandType, commandPayload) as LinkCommandPayload
    assertEquals(LinkMessageTypes.LINK_COMMAND, commandType)
    assertEquals(command.action, decodedCommand.action)
    assertEquals(command.trackId, decodedCommand.trackId)
    assertEquals(command.position, decodedCommand.position)
    assertEquals(command.fromDeviceId, decodedCommand.fromDeviceId)
    assertEquals(command.fromDeviceName, decodedCommand.fromDeviceName)

    val state =
        LinkStatePayload(
            currentTrack = TrackInfo(id = "abc", title = "Song", artist = "Artist", duration = 200_000L),
            isPlaying = false,
            position = 0L,
            lastUpdate = 1_000L,
            volume = 0.5f,
        )
    val (stateType, statePayload) = codec.decode(codec.encode(LinkMessageTypes.LINK_STATE, state))
    val decodedState = codec.decodePayload(stateType, statePayload) as LinkStatePayload
    assertEquals(LinkMessageTypes.LINK_STATE, stateType)
    assertEquals(false, decodedState.isPlaying)
    assertEquals("abc", decodedState.currentTrack?.id)
    assertEquals(0.5f, decodedState.volume)
}

@Test
fun `muzik link handoff payloads survive protobuf round trips`() {
    val handoff = LinkHandoffPayload(targetDeviceId = "desktop")
    val (handoffType, handoffPayload) = codec.decode(codec.encode(LinkMessageTypes.LINK_HANDOFF, handoff))
    assertEquals(LinkMessageTypes.LINK_HANDOFF, handoffType)
    val decodedHandoff =
        Listentogether.LinkHandoffPayload.parseFrom(handoffPayload)
    assertEquals("desktop", decodedHandoff.targetDeviceId)

    val start =
        LinkHandoffStartPayload(
            state = LinkStatePayload(isPlaying = true, position = 5_000L),
            fromDeviceId = "phone",
        )
    val (startType, startPayload) = codec.decode(codec.encode(LinkMessageTypes.LINK_HANDOFF_START, start))
    val decodedStart = codec.decodePayload(startType, startPayload) as LinkHandoffStartPayload
    assertEquals(LinkMessageTypes.LINK_HANDOFF_START, startType)
    assertEquals("phone", decodedStart.fromDeviceId)
    assertEquals(5_000L, decodedStart.state?.position)
}
