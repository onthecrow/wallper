package com.onthecrow.wallper.presentation.crop.model

import androidx.compose.ui.geometry.Rect

sealed interface CropperEvent {
    data class CreateWallpaper(val rect: Rect): CropperEvent
}