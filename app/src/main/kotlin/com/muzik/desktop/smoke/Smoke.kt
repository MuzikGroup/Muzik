package com.muzik.desktop.smoke

import com.muzik.desktop.player.ffmpeg.AudioDecoder
import com.muzik.desktop.player.ffmpeg.AudioPlayer
import com.muzik.desktop.player.ffmpeg.HttpRangeInputStream
import com.muzik.desktop.player.ffmpeg.SabrInputStream
import com.muzik.desktop.youtube.Extraction
import com.muzik.desktop.youtube.InnerTube
import com.muzik.desktop.youtube.SearchFilter
import javax.sound.sampled.LineUnavailableException

/**
 * Headless end-to-end smoke test of the desktop playback pipeline, exactly
 * what a PC user experiences: search -> InnerTubeX extraction -> FFmpeg
 * decode, and when an audio device is present, real Java Sound rendering.
 * Run with: java -Djava.awt.headless=true -cp uber.jar com.muzik.desktop.smoke.SmokeKt
 */
suspend fun main() {
    println("== Muzik Desktop smoke ==")

    val page = InnerTube.search("adele hello", SearchFilter.SONGS)
    println("search songs: ${page.songs.size}")
    require(page.songs.isNotEmpty()) { "search returned no songs" }
    page.songs.take(3).forEach { println("  - ${it.title} | ${it.artist} | ${it.durationText}") }
    require(page.songs.any { it.title != "Unknown" }) { "search titles failed to parse" }

    val song = page.songs.first()
    println("radio extension: ${InnerTube.radio(song.videoId).size} tracks")

    val albumPage = InnerTube.search("adele 30", SearchFilter.ALBUMS)
    if (albumPage.albums.isNotEmpty()) {
        val detail = InnerTube.album(albumPage.albums.first().browseId)
        println("album: ${detail.album.title} tracks=${detail.tracks.size}")
        require(detail.tracks.isNotEmpty()) { "album page returned no tracks" }
    }

    val stream = Extraction.resolve(song.videoId, highQuality = false)
    println(
        "resolved: profile=${stream.profileId} itag=${stream.itag} bitrate=${stream.bitrate} " +
            "mime=${stream.mimeType} size=${stream.contentLengthBytes} sabr=${stream.sabrBootstrap != null}",
    )

    var rendered = false
    try {
        smokeRender(stream)
        rendered = true
    } catch (e: Exception) {
        val noAudioDevice = generateSequence<Throwable>(e) { it.cause }.any { it is LineUnavailableException }
        if (!noAudioDevice) throw e
        println("no audio device on this host — falling back to decode-only verification")
    }
    if (!rendered) smokeDecodeOnly(stream)

    println("SMOKE_OK")
}

/** Full player path: FFmpeg decode + Java Sound render for six seconds. */
private fun smokeRender(
    stream: Extraction.ResolvedStream,
) {
    val completed = java.util.concurrent.CountDownLatch(1)
    var failure: Throwable? = null
    val player =
        AudioPlayer(
            onPlaybackCompleted = { completed.countDown() },
            onPlaybackFailed = { _, t -> failure = t; completed.countDown() },
        )
    player.setNormalizationGain(Extraction.normalizationGain(stream.loudnessDb))

    val prepared = player.prepare(stream)
    require(prepared) { "player failed to prepare: ${player.prepareFailure?.message}" }
    player.play()

    val startAt = player.getCurrentPosition()
    Thread.sleep(6_000)
    val pos = player.getCurrentPosition()
    val dur = player.getDuration()
    println("played: positionMs=$pos durationMs=$dur")
    player.close()

    if (failure != null) {
        throw IllegalStateException("playback failed during smoke: ${failure?.message}", failure)
    }
    require(pos > startAt + 3_000) { "position did not advance: $startAt -> $pos" }
    if (dur > 0) require(dur > 30_000) { "unreasonable duration: $dur" }
}

private fun AudioPlayer.prepare(stream: Extraction.ResolvedStream): Boolean =
    when {
        stream.sabrBootstrap != null -> {
            val bootstrap = stream.sabrBootstrap
            val input =
                SabrInputStream(
                    mediaSize = bootstrap.contentLengthBytes ?: stream.contentLengthBytes,
                    expectedDurationMs = bootstrap.durationMs,
                    streamFactory = { initialPlayerTimeMs, playbackPositionMs ->
                        com.metrolist.innertubex.sabr.SabrAudioStream(
                            httpClient = InnerTube.http,
                            bootstrap = bootstrap,
                            playbackPositionMs = playbackPositionMs,
                            initialPlayerTimeMs = initialPlayerTimeMs,
                        ).chunks()
                    },
                )
            val max = (bootstrap.contentLengthBytes ?: stream.contentLengthBytes ?: 1L)
                .coerceIn(1L, Int.MAX_VALUE.toLong() - 8L).toInt()
            val ok = prepareStream(input, max, startPaused = false)
            if (!ok) runCatching { input.close() }
            ok
        }
        stream.contentLengthBytes != null && stream.contentLengthBytes > 0L -> {
            val size = stream.contentLengthBytes
            val input =
                HttpRangeInputStream(
                    url = stream.url,
                    requestHeaders = stream.headers,
                    mediaSize = size,
                    chunkSize = stream.rangeChunkSizeBytes.takeIf { it > 0L }?.toInt() ?: 1_048_576,
                )
            val ok = prepareStream(input, size.coerceIn(1L, Int.MAX_VALUE.toLong() - 8L).toInt(), startPaused = false)
            if (!ok) runCatching { input.close() }
            ok
        }
        else -> prepareFile(stream.url, stream.headers, startPaused = false)
    }

/** Device-less path: prove the media is real and decodable by FFmpeg itself. */
private fun smokeDecodeOnly(stream: Extraction.ResolvedStream) {
    val decoder =
        when {
            stream.sabrBootstrap != null -> {
                val bootstrap = stream.sabrBootstrap
                val input =
                    SabrInputStream(
                        mediaSize = bootstrap.contentLengthBytes ?: stream.contentLengthBytes,
                        expectedDurationMs = bootstrap.durationMs,
                        streamFactory = { initialPlayerTimeMs, playbackPositionMs ->
                            com.metrolist.innertubex.sabr.SabrAudioStream(
                                httpClient = InnerTube.http,
                                bootstrap = bootstrap,
                                playbackPositionMs = playbackPositionMs,
                                initialPlayerTimeMs = initialPlayerTimeMs,
                            ).chunks()
                        },
                    )
                AudioDecoder(input, (bootstrap.contentLengthBytes ?: stream.contentLengthBytes ?: 1L).toInt())
            }
            stream.contentLengthBytes != null && stream.contentLengthBytes > 0L -> AudioDecoder(
                HttpRangeInputStream(
                    url = stream.url,
                    requestHeaders = stream.headers,
                    mediaSize = stream.contentLengthBytes,
                    chunkSize = stream.rangeChunkSizeBytes.takeIf { it > 0L }?.toInt() ?: 1_048_576,
                ),
                stream.contentLengthBytes.coerceIn(1L, Int.MAX_VALUE.toLong() - 8L).toInt(),
            )
            else -> AudioDecoder(stream.url, stream.headers)
        }
    try {
        println("decoded: sampleRate=${decoder.sampleRate} channels=${decoder.audioChannels} durationMs=${decoder.duration}")
        require(decoder.sampleRate > 0) { "no audio track reported" }
        require(decoder.audioChannels > 0) { "no channels reported" }
        var bytes = 0L
        var frames = 0
        val deadline = System.currentTimeMillis() + 20_000
        while (bytes < 1_500_000 && System.currentTimeMillis() < deadline) {
            val pcm = decoder.readPCMFrame(null) ?: break
            bytes += pcm.size
            frames++
        }
        println("decoded PCM: frames=$frames bytes=$bytes")
        require(bytes > 1_000_000) { "decoded too little PCM: $bytes bytes" }
    } finally {
        decoder.close()
    }
}
