package com.muzik.desktop.youtube

import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.sabr.SabrBootstrap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.pow

/**
 * Playback stream resolution built on InnerTubeX's StreamExtractor — the exact
 * extraction stack Metrolist desktop uses: multi-client fallback with health
 * tracking, player-config refresh, signature-cipher solving through hosted
 * QuackJS, PO-token contracts, and direct/HLS/SABR transport selection.
 *
 * The resolver returns every field the player needs to open the stream
 * (URL, request headers, content length, bounded-range contract, loudness,
 * SABR bootstrap) and a plain-language failure when extraction is impossible.
 */
object Extraction {

    class ExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Everything the player needs to open and render one track. */
    data class ResolvedStream(
        val videoId: String,
        val url: String,
        val headers: Map<String, String>,
        val loudnessDb: Double?,
        val contentLengthBytes: Long?,
        val itag: Int,
        val mimeType: String?,
        val bitrate: Int?,
        val clientName: String,
        val profileId: String,
        val requireBoundedRange: Boolean,
        val useRangeChunks: Boolean,
        val rangeChunkSizeBytes: Long,
        val sabrBootstrap: SabrBootstrap?,
    )

    /**
     * Diagnostics channel. Muted unless MUZIK_EXTRACTION_DEBUG=1; on failure
     * the last events still tell us exactly which client profile failed.
     */
    internal val logger: InnerTubeLogger = InnerTubeLogger { event ->
        if (System.getenv("MUZIK_EXTRACTION_DEBUG") == "1" || event.level != InnerTubeLogLevel.DEBUG) {
            println("[${event.level}] ${event.tag}: ${event.message}${if (event.details.isEmpty()) "" else " ${event.details}"}")
        }
    }

    /** PO tokens for the token-gated clients; tokenless profiles stay first. */
    private val tokenProvider: TokenProvider by lazy { DesktopTokenProvider() }

    @Volatile
    private var extractor: StreamExtractor? = null
    private val bootMutex = Mutex()

    /** Lazily wires the extraction stack (downloads + compiles the player on first use). */
    suspend fun streamExtractor(): StreamExtractor =
        extractor ?: bootMutex.withLock {
            extractor ?: run {
                val configStore =
                    RemotePlayerConfigStore(
                        httpClient = InnerTube.http,
                        repository = PlayerConfigRepository.disabled(),
                    )
                val cipher = YouTubeCipherService(InnerTube.http, configStore, logger)
                val built =
                    InnerTubeExtractor(
                        configParser =
                            YtConfigParserImpl(
                                InnerTube.http,
                                InnerTube.client,
                                configStore,
                                cipherService = cipher,
                            ),
                        cipherService = cipher,
                        innerTube = InnerTube.client,
                        tokenProvider = tokenProvider,
                        logger = logger,
                    )
                extractor = built
                built
            }
        }

    suspend fun prewarm() {
        runCatching { streamExtractor().prewarm() }
    }

    /**
     * Resolves a playable audio stream for [videoId]. Throws
     * [ExtractionException] with a user-presentable message when every
     * profile fails — the same guarantees Metrolist playback gives.
     */
    suspend fun resolve(videoId: String, highQuality: Boolean): ResolvedStream {
        val quality = if (highQuality) AudioQuality.HIGH else AudioQuality.AUTO
        val hints = ContentHints(wantVideo = false)
        val stream =
            try {
                streamExtractor().extract(videoId, hints, audioQuality = quality)
            } catch (e: StreamResolveException) {
                throw ExtractionException(
                    humanMessage(e),
                    e,
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw ExtractionException(
                    "Couldn't reach YouTube for playback. Check your network connection.",
                    e,
                )
            } ?: throw ExtractionException(
                "This track has no playable audio stream. Try the next song.",
            )
        return ResolvedStream(
            videoId = stream.videoId,
            url = stream.audioUrl,
            headers = stream.headers,
            loudnessDb = stream.loudnessDb,
            contentLengthBytes = stream.contentLengthBytes,
            itag = stream.itag,
            mimeType = stream.mimeType,
            bitrate = stream.bitrate,
            clientName = stream.clientName,
            profileId = stream.profileId,
            requireBoundedRange = stream.requireBoundedRange,
            useRangeChunks = stream.useRangeChunks,
            rangeChunkSizeBytes = stream.rangeChunkSizeBytes,
            sabrBootstrap = stream.sabrBootstrap,
        )
    }

    /** Maps the library's failure kinds to text a listener can act on. */
    private fun humanMessage(e: StreamResolveException): String {
        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        return when {
            detail.contains("Sign in to confirm", ignoreCase = true) ||
                detail.contains("bot", ignoreCase = true) ->
                "YouTube is bot-gating this network. Wait a few minutes or switch networks, then try again."
            detail.contains("403", ignoreCase = true) ||
                detail.contains("forbidden", ignoreCase = true) ->
                "YouTube refused the stream (HTTP 403). Wait a moment and try again."
            detail.contains("timeout", ignoreCase = true) ->
                "YouTube took too long to prepare this track. Try again."
            else -> "Couldn't load this track.\n$detail"
        }
    }

    /** Metrolist's loudness normalization (balanced target of -14 LUFS). */
    fun normalizationGain(loudnessDb: Double?): Float {
        val measured = loudnessDb?.takeIf { it.isFinite() } ?: return 1f
        val measuredLufs = measured + (-7f) // AGGRESSIVE anchor, as upstream
        val gainMb = (((-14f) - measuredLufs) * 100.0).toInt().coerceIn(-1500, 300)
        return 10.0.pow(gainMb / 2000.0).toFloat()
    }
}
