package com.onthecrow.wallper.presentation.wallpapers.models

sealed interface WallpapersAction {
    data class NavigateToCropper(val uri: String) : WallpapersAction
}