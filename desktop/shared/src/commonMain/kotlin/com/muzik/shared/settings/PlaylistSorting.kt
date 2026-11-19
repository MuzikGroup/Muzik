package com.muzik.shared.settings

import com.muzik.shared.model.CollectionSort
import com.muzik.shared.model.PlaylistSongSort
import com.muzik.shared.model.SortDirection
import kotlinx.serialization.Serializable

@Serializable
data class SavedPlaylistSongSorting(
    val sort: PlaylistSongSort,
    val direction: SortDirection,
)

@Serializable
data class SavedPlaylistCollectionSorting(
    val sort: CollectionSort,
    val direction: SortDirection,
)
