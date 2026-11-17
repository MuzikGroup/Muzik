package com.muzik.desktop.youtube

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Compact InnerTube client for YouTube Music.
 *
 * Browsing (search, suggestions, album/artist pages, radio) uses the WEB_REMIX
 * music client, which is accepted tokenless. Playback stream resolution lives
 * in [StreamResolver], which follows the multi-profile, tokenless-first
 * approach measured by the NewPipeExtractor community (GPL-3.0) — see that
 * project for the full-blown implementation with SABR and attestation support.
 */
object InnerTube {

    private val http = HttpClient(io.ktor.client.engine.cio.CIO) {
        expectSuccess = false
    }

    private const val MUSIC = "https://music.youtube.com"

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val webRemix = buildJsonObject {
        put("clientName", "WEB_REMIX")
        put("clientVersion", "1.20250310.01.00")
        put("hl", "en")
        put("gl", "US")
    }

    // ---------------------------------------------------------------- visitor

    @Volatile
    private var cachedVisitorData: String? = null
    private var visitorFetchedAt: Long = 0
    private val visitorMutex = Mutex()

    /**
     * YouTube's watch-config visitor identity. Required for the tokenless
     * VISIONOS playback profile; harmless everywhere else.
     */
    suspend fun visitorData(): String? = visitorMutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedVisitorData != null && now - visitorFetchedAt < 30 * 60 * 1000L) {
            return cachedVisitorData
        }
        try {
            val html = http.get(MUSIC) {
                headers { append(HttpHeaders.UserAgent, BROWSER_UA) }
            }.bodyAsText()
            val m = Regex("\"VISITOR_DATA\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)
            val raw = m?.groupValues?.get(1)
            cachedVisitorData = raw?.let(::jsonUnescape)
            visitorFetchedAt = now
        } catch (_: Exception) {
            // Keep previous value on transient failure.
        }
        if (cachedVisitorData == null) {
            // The music host may serve a consent page without the watch config;
            // the main host always embeds a visitor id in its bootstrap HTML.
            try {
                val html = http.get("https://www.youtube.com") {
                    headers { append(HttpHeaders.UserAgent, BROWSER_UA) }
                }.bodyAsText()
                val m = Regex("\"visitorData\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)
                    ?: Regex("\"VISITOR_DATA\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(html)
                val raw = m?.groupValues?.get(1)
                cachedVisitorData = raw?.let(::jsonUnescape)
                visitorFetchedAt = System.currentTimeMillis()
            } catch (_: Exception) {
            }
        }
        cachedVisitorData
    }

    /** Drops the cached visitor identity so the next call re-fetches a fresh one. */
    fun invalidateVisitor() {
        cachedVisitorData = null
        visitorFetchedAt = 0
    }

    private fun jsonUnescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    'u' -> {
                        val hex = s.substring(i + 2, (i + 6).coerceAtMost(s.length))
                        hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                        i += 4
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------ http

    private suspend fun post(endpoint: String, body: JsonObject, extra: Map<String, String> = emptyMap()): JsonObject {
        val visitor = visitorData()
        val payload = buildJsonObject {
            put("context", buildJsonObject { put("client", webRemix) })
            body.forEach { (k, v) -> put(k, v) }
        }
        val text = http.post("$MUSIC/youtubei/v1/$endpoint?prettyPrint=false") {
            headers {
                append(HttpHeaders.UserAgent, BROWSER_UA)
                append(HttpHeaders.Origin, MUSIC)
                append(HttpHeaders.Referrer, "$MUSIC/")
                append("X-YouTube-Client-Name", "67")
                append("X-YouTube-Client-Version", str(webRemix, "clientVersion") ?: "")
                visitor?.let { append("X-Goog-Visitor-Id", it) }
                extra.forEach { (k, v) -> append(k, v) }
            }
            setBody(payload.toString())
        }.bodyAsText()
        return obj(innertubeJson.parseToJsonElement(text.ifEmpty { "{}" }))
    }

    // ---------------------------------------------------------------- search

    suspend fun search(query: String, filter: SearchFilter, continuation: String? = null): SearchPage {
        val body = buildJsonObject {
            if (continuation != null) put("continuation", JsonPrimitive(continuation)) else {
                put("query", JsonPrimitive(query))
                put("params", JsonPrimitive(filter.params))
            }
        }
        val root = post("search", body)
        return parseSearch(root, filter)
    }

    private fun parseSearch(root: JsonObject, filter: SearchFilter): SearchPage {
        val songs = mutableListOf<Song>()
        val albums = mutableListOf<Album>()
        val artists = mutableListOf<Artist>()
        val items = collect(root, "musicResponsiveListItemRenderer")
        for (item in items) {
            when (filter) {
                SearchFilter.ALBUMS -> parseAlbumFromListItem(item)?.let(albums::add)
                SearchFilter.ARTISTS -> parseArtistFromListItem(item)?.let(artists::add)
                else -> parseSong(item, filter == SearchFilter.VIDEOS)?.let(songs::add)
            }
        }
        // Album/artist tabs often ship musicTwoRowItemRenderer cards as well.
        if (filter == SearchFilter.ALBUMS || filter == SearchFilter.ARTISTS) {
            for (card in collect(root, "musicTwoRowItemRenderer")) {
                if (filter == SearchFilter.ALBUMS) parseAlbumCard(card)?.let(albums::add)
                else parseArtistCard(card)?.let(artists::add)
            }
        }
        val continuation = collect(root, "continuationItemRenderer")
            .firstOrNull()
            ?.let { str(obj(it), "continuationEndpoint", "continuationCommand", "token") }
        return SearchPage(songs, albums, artists, continuation)
    }

    suspend fun suggestions(input: String): List<String> {
        val root = post("music/get_search_suggestions", buildJsonObject {
            put("input", JsonPrimitive(input))
        })
        return collect(root, "searchSuggestionRenderer")
            .mapNotNull { runsText(obj(it), "suggestion", "runs").takeIf { t -> t.isNotBlank() } }
    }

    // ----------------------------------------------------------------- album

    suspend fun album(browseId: String): AlbumDetail {
        val root = post("browse", buildJsonObject { put("browseId", JsonPrimitive(browseId)) })
        val header = collect(root, "musicDetailHeaderRenderer").firstOrNull()
            ?: collect(root, "musicResponsiveHeaderRenderer").firstOrNull()
            ?: collect(root, "musicImmersiveHeaderRenderer").firstOrNull()
        val title = header?.let { runsText(obj(it), "title", "runs") }.orEmpty().ifBlank { "Album" }
        val subtitleRuns = header?.let { arrAt(obj(it), "subtitle", "runs") } ?: kotlinx.serialization.json.JsonArray(emptyList())
        val texts = subtitleRuns.mapNotNull { str(obj(it), "text") }
        val artist = texts.firstOrNull { it.isNotBlank() && it != "•" && !it.contains("•") } ?: ""
        val year = texts.lastOrNull { it.length >= 4 && it.any { ch -> ch.isDigit() } }
        val thumb = header?.let { bestThumb(obj(it), "thumbnail", "thumbnail", "thumbnails") }
            ?: header?.let { bestThumb(obj(it), "thumbnail", "croppedMusicThumbnailRenderer", "thumbnail", "thumbnails") }

        val shelf = collect(root, "musicShelfRenderer").firstOrNull()
        val playlistId = shelf?.let { str(obj(it), "playlistId") }
        val tracks = shelf?.let { sh ->
            arrAt(obj(sh), "contents").mapNotNull { parseSong(obj(it), false) }
        } ?: emptyList()

        val artistBrowseId = header?.let {
            firstNavigableRun(obj(it), "subtitle", "runs")["browseEndpoint"]
        }?.let { str(obj(it), "browseEndpoint", "browseId") }

        return AlbumDetail(
            Album(
                browseId = browseId,
                title = title,
                artist = artist,
                artistBrowseId = artistBrowseId,
                year = year,
                thumbnail = thumb,
                playlistId = playlistId,
            ),
            tracks,
        )
    }

    // ---------------------------------------------------------------- artist

    suspend fun artist(browseId: String): Artist {
        val root = post("browse", buildJsonObject { put("browseId", JsonPrimitive(browseId)) })
        val header = collect(root, "musicImmersiveHeaderRenderer").firstOrNull()
            ?: collect(root, "musicDetailHeaderRenderer").firstOrNull()
        val name = header?.let { runsText(obj(it), "title", "runs") }.orEmpty().ifBlank { "Artist" }
        val thumb = header?.let { bestThumb(obj(it), "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails") }

        val songs = mutableListOf<Song>()
        val albums = mutableListOf<Album>()
        for (shelf in collect(root, "musicShelfRenderer")) {
            arrAt(obj(shelf), "contents").forEach { item ->
                parseSong(obj(item), false)?.let(songs::add)
            }
        }
        for (carousel in collect(root, "musicCarouselShelfRenderer")) {
            arrAt(obj(carousel), "contents").forEach { cell ->
                val card = obj(cell)["musicTwoRowItemRenderer"] ?: return@forEach
                parseAlbumCard(obj(card))?.let(albums::add)
            }
        }
        return Artist(browseId, name, thumb, songs.distinctBy { it.videoId }, albums.distinctBy { it.browseId })
    }

    // ----------------------------------------------------------------- radio

    /** Autoplay "up next" queue seeded by a song. */
    suspend fun radio(videoId: String): List<Song> {
        val root = post("next", buildJsonObject {
            put("videoId", JsonPrimitive(videoId))
            put("playlistId", JsonPrimitive("RDAMVM$videoId"))
        })
        // The queue panel has moved between response revisions
        // (twoColumnWatchNextResults.playlist.playlist vs
        // singleColumnMusicWatchNextResultsRenderer...musicQueueRenderer),
        // so collect the renderers wherever they sit in the tree.
        return collect(root, "playlistPanelVideoRenderer").mapNotNull { r ->
            val vid = str(obj(r), "videoId") ?: return@mapNotNull null
            Song(
                videoId = vid,
                title = runsText(obj(r), "title", "runs").ifBlank { "Unknown" },
                artist = runsText(obj(r), "shortBylineText", "runs").ifBlank { "Unknown" },
                durationText = runsText(obj(r), "lengthText", "runs").ifBlank { null },
                durationSeconds = parseDuration(runsText(obj(r), "lengthText", "runs")),
                thumbnail = bestThumb(obj(r), "thumbnail", "thumbnails"),
            )
        }.distinctBy { it.videoId }
    }

    // -------------------------------------------------------------- parsers

    private fun parseSong(item: JsonObject, isVideo: Boolean): Song? {
        val videoId = str(item, "playlistItemData", "videoId")
            ?: str(item, "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId")
            ?: str(firstNavigableRun(item, "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs"), "watchEndpoint", "videoId")
            ?: return null
        val title = runsText(item, "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs").ifBlank { "Unknown" }

        var artist = ""
        var artistBrowseId: String? = null
        var album: String? = null
        var albumBrowseId: String? = null
        var views: String? = null
        var detectedVideoType: String? = null

        val columns = arrAt(item, "flexColumns")
        for (ci in 1 until columns.size) {
            val col = obj(columns[ci])["musicResponsiveListItemFlexColumnRenderer"] ?: continue
            val runs = arrAt(obj(col), "text", "runs")
            for (run in runs) {
                val ro = obj(run)
                val text = str(ro, "text")?.trim() ?: continue
                if (text == "•" || text.isEmpty()) continue
                val pageType = str(ro, "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")
                when {
                    pageType == "MUSIC_PAGE_TYPE_ARTIST" && artist.isEmpty() -> {
                        artist = text
                        artistBrowseId = str(ro, "browseEndpoint", "browseId")
                    }
                    pageType == "MUSIC_PAGE_TYPE_ALBUM" && album == null -> {
                        album = text
                        albumBrowseId = str(ro, "browseEndpoint", "browseId")
                    }
                    else -> {
                        if (views == null && (text.endsWith("views") || text.endsWith("plays"))) views = text
                        else if (album == null && artist.isEmpty()) artist = text
                        else if (album == null && artist.isNotEmpty() && ci >= 2) album = text
                    }
                }
            }
        }

        val musicVideoType = str(item, "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig", "musicVideoType")
        detectedVideoType = musicVideoType
        val video = isVideo || detectedVideoType?.let { it != "MUSIC_VIDEO_TYPE_ATV" } ?: false

        val durationText = arrAt(item, "fixedColumns")
            .firstOrNull()
            ?.let { runsText(obj(it), "musicResponsiveListItemFixedColumnRenderer", "text", "runs") }
            ?.takeIf { it.isNotBlank() }

        val thumb = bestThumb(item, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")
            ?: bestThumb(item, "thumbnail", "thumbnails")

        return Song(
            videoId = videoId,
            title = title,
            artist = artist.ifBlank { "Unknown artist" },
            artistBrowseId = artistBrowseId,
            album = album,
            albumBrowseId = albumBrowseId,
            durationText = durationText,
            durationSeconds = parseDuration(durationText),
            thumbnail = thumb,
            isVideo = video,
            views = views,
        )
    }

    private fun parseAlbumFromListItem(item: JsonObject): Album? {
        val browseId = str(firstNavigableRun(item, "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs"), "browseEndpoint", "browseId") ?: return null
        val title = runsText(item, "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs").ifBlank { return null }
        var artist = ""
        var artistBrowseId: String? = null
        var year: String? = null
        for (ci in 1 until arrAt(item, "flexColumns").size) {
            val col = obj(arrAt(item, "flexColumns")[ci])["musicResponsiveListItemFlexColumnRenderer"] ?: continue
            val run = firstNavigableRun(obj(col), "text", "runs")
            val pageType = str(run, "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")
            if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                artist = str(run, "text") ?: ""
                artistBrowseId = str(run, "browseEndpoint", "browseId")
            }
        }
        year = arrAt(item, "flexColumns")
            .lastOrNull()
            ?.let { runsText(obj(it), "musicResponsiveListItemFlexColumnRenderer", "text", "runs") }
            ?.split("•")
            ?.lastOrNull()
            ?.trim()
            ?.takeIf { it.length >= 4 }
        return Album(
            browseId = browseId,
            title = title,
            artist = artist.ifBlank { "Various" },
            artistBrowseId = artistBrowseId,
            year = year,
            thumbnail = bestThumb(item, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"),
        )
    }

    private fun parseArtistFromListItem(item: JsonObject): Artist? {
        val browseId = str(firstNavigableRun(item, "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs"), "browseEndpoint", "browseId") ?: return null
        val name = runsText(item, "flexColumns", 0, "musicResponsiveListItemFlexColumnRenderer", "text", "runs").ifBlank { return null }
        val subs = arrAt(item, "flexColumns")
            .getOrNull(1)
            ?.let { runsText(obj(it), "musicResponsiveListItemFlexColumnRenderer", "text", "runs") }
        return Artist(browseId = browseId, name = name, thumbnail = bestThumb(item, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
    }

    private fun parseAlbumCard(card: JsonObject): Album? {
        val browseId = str(card, "navigationEndpoint", "browseEndpoint", "browseId") ?: return null
        if (!browseId.startsWith("MPREb")) return null
        val title = runsText(card, "title", "runs").ifBlank { return null }
        val subtitle = arrAt(card, "subtitle", "runs").mapNotNull { str(obj(it), "text") }
        val artist = subtitle.firstOrNull { it.isNotBlank() && it != "•" } ?: "Various"
        val year = subtitle.lastOrNull { it.isNotBlank() && it != "•" && it.any { ch -> ch.isDigit() } }
        val playlistId = str(card, "navigationEndpoint", "watchEndpoint", "playlistId")
        return Album(
            browseId = browseId,
            title = title,
            artist = artist,
            year = year,
            thumbnail = bestThumb(card, "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails")
                ?: bestThumb(card, "thumbnailRenderer", "musicTwoRowItemThumbnailRenderer", "thumbnail", "thumbnails")
                ?: bestThumb(card, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"),
            playlistId = playlistId,
        )
    }

    private fun parseArtistCard(card: JsonObject): Artist? {
        val browseId = str(card, "navigationEndpoint", "browseEndpoint", "browseId") ?: return null
        if (!browseId.startsWith("UC")) return null
        val name = runsText(card, "title", "runs").ifBlank { return null }
        return Artist(
            browseId = browseId,
            name = name,
            thumbnail = bestThumb(card, "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails")
                ?: bestThumb(card, "thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"),
        )
    }

    // ---------------------------------------------------------------- utils

    private fun collect(root: JsonObject, key: String): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        fun walk(e: kotlinx.serialization.json.JsonElement?) {
            when (e) {
                is JsonObject -> {
                    val child = e[key]
                    if (child is JsonObject) out.add(child)
                    e.values.forEach(::walk)
                }
                is kotlinx.serialization.json.JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    fun parseDuration(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        val parts = text.split(":", " ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return 0
        var secs = 0
        for (p in parts) {
            val v = p.toIntOrNull() ?: return 0
            secs = secs * 60 + v
        }
        return secs
    }
}
