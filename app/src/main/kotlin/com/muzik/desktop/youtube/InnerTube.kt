package com.muzik.desktop.youtube

import com.metrolist.innertubex.InnerTube as InnerTubeClient
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * YouTube Music browsing facade backed by InnerTubeX — the same battle-tested
 * client library Metrolist uses. Search, suggestions, album/artist pages and
 * radio queues all ride on the library's WEB_REMIX transport, which keeps
 * visitor identity, client versions and locale handling current without
 * hand-rolled profile guesswork.
 *
 * Playback stream resolution lives in [Extraction], built on the library's
 * StreamExtractor (multi-client fallback, SABR, signature ciphers).
 */
object InnerTube {

    val http: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
        install(io.ktor.client.plugins.compression.ContentEncoding) {
            gzip(0.9f)
            deflate(0.8f)
        }
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
    }

    internal val client: InnerTubeClient = InnerTubeClient(http)

    // ---------------------------------------------------------------- search

    suspend fun search(query: String, filter: SearchFilter, continuation: String? = null): SearchPage {
        val text = client.search(
            client = YouTubeClient.WEB_REMIX,
            query = query.takeIf { continuation == null },
            params = filter.params.takeIf { continuation == null },
            continuation = continuation,
        ).bodyAsText()
        return parseSearch(obj(innertubeJson.parseToJsonElement(text.ifEmpty { "{}" })), filter)
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
        val text = client.getSearchSuggestions(YouTubeClient.WEB_REMIX, input).bodyAsText()
        val root = obj(innertubeJson.parseToJsonElement(text.ifEmpty { "{}" }))
        return collect(root, "searchSuggestionRenderer")
            .mapNotNull { runsText(obj(it), "suggestion", "runs").takeIf { t -> t.isNotBlank() } }
    }

    // ----------------------------------------------------------------- album

    suspend fun album(browseId: String): AlbumDetail {
        val text = client.browse(YouTubeClient.WEB_REMIX, browseId = browseId).bodyAsText()
        val root = obj(innertubeJson.parseToJsonElement(text.ifEmpty { "{}" }))
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
        val text = client.browse(YouTubeClient.WEB_REMIX, browseId = browseId).bodyAsText()
        val root = obj(innertubeJson.parseToJsonElement(text.ifEmpty { "{}" }))
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
        val text = client.next(
            client = YouTubeClient.WEB_REMIX,
            videoId = videoId,
            playlistId = "RDAMVM$videoId",
        ).bodyAsText()
        val root = obj(innertubeJson.parseToJsonElement(text.ifEmpty { "{}" }))
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
        val video = isVideo || musicVideoType?.let { it != "MUSIC_VIDEO_TYPE_ATV" } ?: false

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
        for (ci in 1 until arrAt(item, "flexColumns").size) {
            val col = obj(arrAt(item, "flexColumns")[ci])["musicResponsiveListItemFlexColumnRenderer"] ?: continue
            val run = firstNavigableRun(obj(col), "text", "runs")
            val pageType = str(run, "browseEndpoint", "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType")
            if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                artist = str(run, "text") ?: ""
                artistBrowseId = str(run, "browseEndpoint", "browseId")
            }
        }
        val year = arrAt(item, "flexColumns")
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
