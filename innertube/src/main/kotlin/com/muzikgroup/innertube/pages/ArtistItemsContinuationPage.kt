package com.muzikgroup.innertube.pages

import com.muzikgroup.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
