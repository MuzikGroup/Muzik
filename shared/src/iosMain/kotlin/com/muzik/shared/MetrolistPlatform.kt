package com.muzik.shared

import com.muzik.shared.api.lastfm.LastFM
import com.muzik.shared.auth.AuthManager
import com.muzik.shared.backup.IosBackupRestoreManager
import com.muzik.shared.db.MusicDatabase
import com.muzik.shared.db.daos.DatabaseDao
import com.muzik.shared.db.getDatabaseBuilder
import com.muzik.shared.discord.DiscordRpcManager
import com.muzik.shared.playback.MusicPlayer
import com.muzik.shared.repository.LyricsRepository
import com.muzik.shared.scrobble.ScrobbleSessions
import com.muzik.shared.settings.BlockedContent
import com.muzik.shared.settings.Settings
import com.muzik.shared.state.GlobalYouTube
import com.muzik.shared.state.SongLikeState
import com.muzik.shared.state.coreModule
import com.muzik.shared.youtube.di.sharedModule
import com.muzik.shared.youtube.playback.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform

object MuzikPlatform {
    private var isInitialized = false

    fun initialize() {
        if (isInitialized) return
        IosBackupRestoreManager().recoverInterruptedRestore()
        isInitialized = true
        startKoin {
            modules(
                coreModule,
                sharedModule,
                module {
                    single<MusicDatabase> { getDatabaseBuilder(null).build() }
                    single<DatabaseDao> { get<MusicDatabase>().dao() }
                    single { MusicPlayer() }
                },
            )
        }

        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY,
            secret = BuildConfig.LASTFM_SECRET,
        )
        LastFM.sessionKey = Settings.shared.lastFmSession.ifBlank { null }
        ScrobbleSessions.restorePersistedAccounts()

        AuthManager().restorePersistedSession()

        val startupScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        startupScope.launch {
            BlockedContent.refreshAiSlopBlocklist()
        }
        startupScope.launch {
            runCatching {
                KoinPlatform.getKoin().get<StreamResolver>().prewarm()
            }
        }
    }

    suspend fun reopenDatabaseAfterRestore(): MusicDatabase {
        val koin = KoinPlatform.getKoin()
        val database = getDatabaseBuilder(null).build()
        koin.declare(database)
        koin.declare(database.dao())
        koin.declare(MusicPlayer())
        koin.declare(
            LyricsRepository(
                lyricsClient = koin.get(),
                lyricsTranslationClient = koin.get(),
                databaseDao = database.dao(),
                youtubeLyricsApi = koin.get(),
                aiModelCatalog = koin.get(),
            ),
        )
        refreshRestoredSettings()
        return database
    }

    suspend fun refreshRestoredSettings() {
        DiscordRpcManager.reloadAfterRestore(GlobalYouTube.client)
        AuthManager().restorePersistedSession()
        ScrobbleSessions.restorePersistedAccounts()
        SongLikeState.refreshFromSettings()
    }
}
