/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.shared.db.entities

import androidx.room3.Embedded
import androidx.room3.Relation

data class EventWithSong(
    @Embedded
    val event: Event,
    @Relation(
        entity = SongEntity::class,
        parentColumns = ["songId"],
        entityColumns = ["id"],
    )
    val song: Song,
    @Relation(parentColumns = ["id"], entityColumns = ["eventId"])
    val snapshot: HistorySnapshot? = null,
)
