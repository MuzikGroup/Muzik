package com.muzik.shared.youtube.di

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamExtractor
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.strategy.ClientFallbackStrategy
import com.metrolist.innertubex.extraction.strategy.ClientHealthMonitor
import com.metrolist.innertubex.extraction.strategy.ContentAwareFallbackStrategy
import com.muzik.shared.BuildConfig
import com.muzik.shared.api.lyrics.AiModelCatalog
import com.muzik.shared.api.lyrics.LyprioLyricsProvider
import com.muzik.shared.api.lyrics.LyricsClient
import com.muzik.shared.api.lyrics.LyricsFetcher
import com.muzik.shared.api.lyrics.LyricsTranslationClient
import com.muzik.shared.api.lyrics.YouTubeLyricsApi
import com.muzik.shared.api.musicvideo.MusicVideoResolver
import com.muzik.shared.repository.LyricsRepository
import com.muzik.shared.state.GlobalYouTube
import com.muzik.shared.youtube.logging.MuzikInnerTubeLogger
import com.muzik.shared.youtube.lyrics.InnerTubeLyricsApi
import com.muzik.shared.youtube.playback.DefaultStreamResolver
import com.muzik.shared.youtube.playback.LocalAudioSource
import com.muzik.shared.youtube.playback.MuzikPlayerConfigRepository
import com.muzik.shared.youtube.playback.PlatformTokenProvider
import com.muzik.shared.youtube.playback.StreamResolver
import com.muzik.shared.youtube.strategy.ClientHealthTracker
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val sharedModule =
    module {
        single {
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        }
        single { RemotePlayerConfigStore(get(), MuzikPlayerConfigRepository, MuzikInnerTubeLogger) }
        single<YtConfigParser> {
            YtConfigParserImpl(
                get(),
                get(),
                get(),
                MuzikInnerTubeLogger,
                cipherService = get<YouTubeCipherService>(),
            )
        }

        single { ClientHealthTracker() }
        single<ClientHealthMonitor> { get<ClientHealthTracker>() }
        single<ClientFallbackStrategy> { ContentAwareFallbackStrategy(get()) }

        single { YouTubeCipherService(get(), get(), MuzikInnerTubeLogger) }

        single<TokenProvider> { PlatformTokenProvider() }

        single<StreamExtractor> {
            InnerTubeExtractor(
                configParser = get(),
                cipherService = get<YouTubeCipherService>(),
                innerTube = get<InnerTube>(),
                fallbackStrategy = get(),
                tokenProvider = get<TokenProvider>(),
                clientHealthMonitor = get(),
                logger = MuzikInnerTubeLogger,
            )
        }

        single<StreamResolver> {
            DefaultStreamResolver(
                extractor = get(),
                innerTube = get(),
                tokenProvider = getOrNull(),
                clientHealthTracker = get(),
                playerConfigRefresh = { get<YouTubeCipherService>().refreshAfterStreamRejection() },
                musicVideoResolver = get(),
                localAudioSource = getOrNull<LocalAudioSource>(),
            )
        }

        single<MusicVideoResolver> { MusicVideoResolver() }

        single { GlobalYouTube.repository }
        single<YouTubeLyricsApi> { InnerTubeLyricsApi(get()) }
        single {
            LyricsClient(
                httpClient = get(),
                youtubeLyricsApi = get(),
                lyprioLyricsProvider =
                    LyprioLyricsProvider.createOrNull(
                        httpClient = get(),
                        baseUrl = BuildConfig.LYPRIO_BASE_URL,
                        signingKeyBase64 = BuildConfig.LYPRIO_HMAC_KEY,
                        allowInsecureHttp = BuildConfig.DEBUG,
                    ),
            )
        }
        single<LyricsFetcher> { get<LyricsClient>() }
        single { LyricsTranslationClient(get(), get()) }
        single { AiModelCatalog(get()) }
        single {
            LyricsRepository(
                lyricsClient = get(),
                lyricsTranslationClient = get(),
                aiModelCatalog = get(),
                databaseDao = getOrNull(),
                youtubeLyricsApi = get(),
            )
        }
    }
