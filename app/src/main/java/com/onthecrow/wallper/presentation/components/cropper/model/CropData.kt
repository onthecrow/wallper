package com.onthecrow.wallper.presentation.components.cropper.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect


/**
 * Class that contains information about
 * current zoom, pan and rotation, and rectangle of zoomed and panned area for cropping [cropRect],
 * and area of overlay as[overlayRect]
 *
 */
@Immutable
data class CropData(
    val overlayRect: Rect,
    val cropRect: Rect
)
