/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.desktop.di

import com.muzik.desktop.state.playback.PlayerState
import com.muzik.desktop.storage.DownloadManager
import com.muzik.desktop.storage.SongCacheManager
import com.muzik.desktop.storage.desktopSongOfflineAvailability
import com.muzik.desktop.youtube.DesktopPageBoundTokenRuntime
import com.muzik.innertubex.extraction.TokenProvider
import com.muzik.shared.db.MusicDatabase
import com.muzik.shared.db.OfflineSongAvailability
import com.muzik.shared.db.daos.DatabaseDao
import com.muzik.shared.db.getDatabaseBuilder
import com.muzik.shared.youtube.playback.DesktopTokenProvider
import com.muzik.shared.youtube.playback.LocalAudioSource
import org.koin.dsl.module

val composeStateModule =
    module {
        single<MusicDatabase> { getDatabaseBuilder().build() }
        single<LocalAudioSource> {
            LocalAudioSource { id, song ->
                DownloadManager.getLocalPlaybackResource(id, song) ?: SongCacheManager.getCachedPlaybackResource(id, song)
            }
        }
        single<DatabaseDao> { get<MusicDatabase>().dao() }
        single<OfflineSongAvailability> { desktopSongOfflineAvailability(get()) }
        single { DesktopPageBoundTokenRuntime() }
        single<TokenProvider> {
            DesktopTokenProvider(get<DesktopPageBoundTokenRuntime>()) {
                val queue = PlayerState.queue.snapshots.value
                queue.displayedItems
                    .asSequence()
                    .drop(queue.currentIndex + 1)
                    .take(2)
                    .map { it.id }
                    .toList()
            }
        }
    }
