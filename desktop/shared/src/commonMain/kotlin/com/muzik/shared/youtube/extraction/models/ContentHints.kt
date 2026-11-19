package com.muzik.shared.youtube.extraction.models

import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.muzik.shared.model.SongItem
import com.muzik.shared.model.isLive
import com.muzik.shared.model.isUploadedType
import com.muzik.shared.model.isVideoSong
import com.muzik.shared.settings.MusicVideoQuality
import com.muzik.shared.settings.Settings
import com.muzik.shared.settings.targetHeight
import com.muzik.shared.utils.getDeviceDisplayMaxVideoHeight
import com.muzik.shared.youtube.playback.PlaybackBandwidthMeter
import com.muzik.shared.youtube.playback.PlaybackNetworkState
import com.muzik.shared.youtube.playback.PlaybackNetworkStateStore

fun SongItem.toContentHints(wantVideo: Boolean? = null): ContentHints {
    val uploaded = isUploaded || isUploadedType(musicVideoType)
    val normalizedType = musicVideoType?.uppercase()
    val devtoolsPlaybackSelection = Settings.shared.devtoolsPlaybackClient

    val kidsHint =
        when {
            normalizedType?.contains("KIDS") == true -> true
            normalizedType?.contains("YTKIDS") == true -> true
            else -> null
        }

    val liveHint = true.takeIf { isLive }

    val quality = Settings.shared.musicVideoQuality
    return ContentHints(
        isExplicit = isExplicit,
        isKidsContent = kidsHint,
        isLive = liveHint,
        isUploaded = uploaded,
        isLocal = isLocal,
        endpointParams = endpointParams,
        playbackClientOverrideId =
            devtoolsPlaybackSelection
                .takeUnless { it == PlaybackClientCatalog.AUTOMATIC_ID || it == PlaybackClientCatalog.SABR_FIRST_ID }
                ?.takeIf { PlaybackClientCatalog.findDevtoolsOverride(it) != null }
                ?: if (liveHint == true) "ANDROID" else null,
        sabrFirst = devtoolsPlaybackSelection == PlaybackClientCatalog.SABR_FIRST_ID,
        wantVideo = !uploaded && (wantVideo ?: (isVideoSong || liveHint == true)),
        maxVideoHeight =
            when (quality) {
                MusicVideoQuality.AUTO -> automaticVideoMaxHeight()
                else -> quality.targetHeight
            },
    )
}

fun automaticVideoMaxHeight(
    networkState: PlaybackNetworkState = PlaybackNetworkStateStore.current,
    bandwidthBps: Long = PlaybackBandwidthMeter.estimatedBandwidthBps,
    deviceMaxHeight: Int = getDeviceDisplayMaxVideoHeight(),
): Int {
    val isCellular = networkState.scope.contains("cell", ignoreCase = true) || !networkState.connected
    val bandwidthCap =
        when {
            bandwidthBps >= 15_000_000L -> 2160
            bandwidthBps >= 8_000_000L -> 1440
            bandwidthBps >= 4_000_000L -> 1080
            bandwidthBps >= 2_000_000L -> 720
            else -> 480
        }
    val effectiveCap = if (isCellular) minOf(720, bandwidthCap) else bandwidthCap
    return minOf(effectiveCap, deviceMaxHeight)
}
