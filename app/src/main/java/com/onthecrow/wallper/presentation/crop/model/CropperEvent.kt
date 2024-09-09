package com.onthecrow.wallper.presentation.crop.model

import androidx.compose.ui.geometry.Rect

sealed interface CropperEvent {
    data class CreateWallpaper(val rect: Rect) : CropperEvent
    data class TimeLineRangeChanged(val newRange: ClosedFloatingPointRange<Float>) : CropperEvent
    data object ShowAdditionalProcessingInfo : CropperEvent
    data object ToggleAdditionalProcessing : CropperEvent
}