package com.muzikgroup.innertube.pages

import com.muzikgroup.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
