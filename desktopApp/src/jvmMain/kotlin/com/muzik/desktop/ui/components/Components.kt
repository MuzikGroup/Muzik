/**
 * Muzik Project (C) 2026
 * Licensed under GPL-3.0 | Built by Ignatius (lead developer) and the Pipo Team
 */

package com.muzik.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import coil3.compose.asPainter
import coil3.request.ImageRequest
import com.muzik.desktop.state.playback.PlayerArtworkPreview
import com.muzik.shared.constants.ThumbnailCornerRadius
import com.muzik.shared.ui.icons.MuzikIcons
import com.muzik.shared.ui.utils.ArtworkBlurStyle
import com.muzik.shared.ui.utils.cachedArtworkBlur
import com.muzik.shared.utils.canonicalArtworkUrl
import coil3.compose.AsyncImage as CoilAsyncImage

@Composable
internal fun AsyncImage(
    url: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    maxDecodeWidth: Int? = null,
    artworkPreview: PlayerArtworkPreview? = null,
    saturation: Float = 1f,
    blur: ArtworkBlurStyle? = null,
) {
    val context = LocalPlatformContext.current
    val canonicalUrl = remember(url) { canonicalArtworkUrl(url) }

    val saturationMatrix =
        remember(saturation) {
            ColorMatrix().apply { setToSaturation(saturation) }
        }
    val saturationFilter =
        remember(saturationMatrix) {
            ColorFilter.colorMatrix(saturationMatrix)
        }

    val imageModifier =
        if (blur != null) {
            modifier.cachedArtworkBlur(blur, if (url.isBlank()) RectangleShape else shape)
        } else {
            modifier.clip(shape)
        }

    if (url.isBlank()) {
        val backgroundModifier = if (blur == null) modifier else imageModifier
        Box(
            backgroundModifier.background(MaterialTheme.colorScheme.surfaceVariant, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MuzikIcons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
        }
        return
    }
    val previewImage = artworkPreview?.image
    val previewPainter = remember(previewImage, context) { previewImage?.asPainter(context) }
    val model =
        remember(url, maxDecodeWidth, context) {
            if (maxDecodeWidth == null) {
                url
            } else {
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(maxDecodeWidth, maxDecodeWidth)
                    .build()
            }
        }
    var hasError by remember(canonicalUrl, artworkPreview) { mutableStateOf(false) }

    Box(imageModifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        key(canonicalUrl, artworkPreview) {
            CoilAsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                placeholder = previewPainter,
                onSuccess = {
                    hasError = false
                    artworkPreview?.retain(it.result.image)
                },
                onError = { hasError = true },
                colorFilter = saturationFilter,
            )
        }
        if (hasError) {
            Icon(
                MuzikIcons.Default.BrokenImage,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            )
        }
    }
}
