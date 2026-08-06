package com.muzikgroup.innertube.pages

import com.muzikgroup.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
