package com.muzikgroup.innertube.models.body

import com.muzikgroup.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
