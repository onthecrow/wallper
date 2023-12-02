package com.onthecrow.wallper.presentation.wallpapers.models

sealed interface WallpapersAction {
    data object ShowToast : WallpapersAction
}