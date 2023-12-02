package com.onthecrow.wallper.presentation.wallpapers.models

sealed interface WallpapersEvent {
    data class OnWallpaperClick(val wallpaperId: Int) : WallpapersEvent
}