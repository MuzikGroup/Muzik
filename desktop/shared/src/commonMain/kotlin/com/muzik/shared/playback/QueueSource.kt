package com.muzik.shared.playback

import com.muzik.shared.model.SongItem

const val QUEUE_SOURCE_ADDED_MANUALLY = "muzik:queue-source:added-manually"
const val QUEUE_SOURCE_AUTO_MIX = "muzik:queue-source:auto-mix"
const val QUEUE_SOURCE_RECOMMENDED = "muzik:queue-source:recommended"

fun String?.isRecommendedQueueSource(): Boolean = this == QUEUE_SOURCE_RECOMMENDED || this == "Recommended"

fun String?.isAutoMixQueueSource(): Boolean = this == QUEUE_SOURCE_AUTO_MIX

fun String?.isRecommendationGeneratedQueueSource(): Boolean = isRecommendedQueueSource() || isAutoMixQueueSource()

fun SongItem.asManuallyAddedQueueItem(): SongItem =
    copy(
        queueSourceTitle =
            queueSourceTitle
                ?.takeUnless(String::isRecommendationGeneratedQueueSource)
                ?: QUEUE_SOURCE_ADDED_MANUALLY,
    )
