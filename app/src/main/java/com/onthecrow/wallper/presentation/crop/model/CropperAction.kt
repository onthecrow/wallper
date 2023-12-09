package com.onthecrow.wallper.presentation.crop.model

sealed interface CropperAction {
    data object NavigateToWallpapersScreen: CropperAction
}