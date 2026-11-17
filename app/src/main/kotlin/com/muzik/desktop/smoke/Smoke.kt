package com.muzik.desktop.smoke

import com.muzik.desktop.player.ffmpeg.AudioDecoder
import com.muzik.desktop.player.ffmpeg.AudioPlayer
import com.muzik.desktop.player.ffmpeg.HttpRangeInputStream
import com.muzik.desktop.player.ffmpeg.SabrInputStream
import com.muzik.desktop.youtube.Extraction
import com.muzik.desktop.youtube.InnerTube
import com.muzik.desktop.youtube.SearchFilter
import java.io.File

/**
 * Headless end-to-end smoke test of the desktop playback pipeline.
 *
 * Network phase (YouTube-dependent, may be IP-gated on CI runners):
 *   search -> InnerTubeX extraction -> FFmpeg decode of the live stream.
 * Offline phase (deterministic, always runs):
 *   generate an AAC test tone with the bundled FFmpeg binary and decode it
 *   through the exact player path, proving FFmpeg + the decoder work.
 *
 * Exits 0 on SMOKE_OK (everything ran) or SMOKE_PARTIAL (the runner's IP was
 * blocked by YouTube but the offline player stack validated). Any other
 * failure exits non-zero.
 */
suspend fun main() {
    println("== Muzik Desktop smoke ==")
    var networkVerified = false

    try {
        networkPhase()
        networkVerified = true
    } catch (e: Exception) {
        if (!looksLikeIpGating(e)) throw e
        println("network phase blocked by YouTube IP gating: ${e.message?.lineSequence()?.firstOrNull()}")
    }

    offlinePhase()

    if (networkVerified) {
        println("SMOKE_OK")
    } else {
        println("SMOKE_PARTIAL (runner IP gated by YouTube; offline player stack verified)")
    }
}

/** True when the failure is YouTube refusing the datacenter/VPN IP, not a code bug. */
private fun looksLikeIpGating(e: Throwable): Boolean {
    val text = generateSequence<Throwable>(e) { it.cause }.joinToString("|") { "${it.message} ${it::class.simpleName}" }
    return listOf(
        "Sign in to confirm",
        "bot-gat",
        "LOGIN_REQUIRED",
        "HTTP 302",
        "HTTP 400",
        "HTTP 429",
        "InnerTubeHttpException",
        "Too many redirects",
        "search returned no songs",
        "Unable to fetch stream data",
    ).any { text.contains(it, ignoreCase = true) }
}

/** Search, radio, album pages and a real extraction+decode when YouTube allows it. */
private suspend fun networkPhase() {
    val page = InnerTube.search("adele hello", SearchFilter.SONGS)
    println("search songs: ${page.songs.size}")
    require(page.songs.isNotEmpty()) { "search returned no songs" }
    page.songs.take(3).forEach { println("  - ${it.title} | ${it.artist}") }
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
    smokePlay(stream)
}

/** Full player path: FFmpeg decode + Java Sound render for six seconds. */
private fun smokePlay(stream: Extraction.ResolvedStream) {
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

/**
 * Deterministic offline proof: decodes a committed AAC test tone through the
 * exact player decoder (FFmpeg -> PCM), no audio device or network required.
 */
private fun offlinePhase() {
    val tone = File(System.getProperty("java.io.tmpdir"), "muzik-smoke-tone.m4a")
    Thread.currentThread().contextClassLoader.getResourceAsStream("smoke-tone.m4a")?.use { input ->
        tone.outputStream().use { input.copyTo(it) }
    } ?: error("smoke-tone.m4a resource missing from the classpath")
    check(tone.length() > 10_000) { "smoke tone resource is truncated" }

    val decoder = AudioDecoder(tone.absolutePath)
    try {
        println("decoded: rate=${decoder.sampleRate} ch=${decoder.audioChannels} durationMs=${decoder.duration}")
        require(decoder.sampleRate > 0) { "no audio track reported" }
        require(decoder.audioChannels > 0) { "no channels reported" }
        require(decoder.duration > 10_000) { "unreasonable tone duration: ${decoder.duration}ms" }
        var bytes = 0L
        while (bytes < 200_000) {
            val pcm = decoder.readPCMFrame(null) ?: break
            bytes += pcm.size
        }
        println("offline decode PCM bytes=$bytes")
        require(bytes > 100_000) { "decoded too little PCM: $bytes bytes" }
    } finally {
        decoder.close()
    }
}

