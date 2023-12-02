package com.onthecrow.wallper.presentation.wallpapers

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable

const val WALLPAPERS_SCREEN = "WallpapersScreen"

fun NavGraphBuilder.wallpapersScreen() {
    composable(route = WALLPAPERS_SCREEN) {
        WallpapersScreen()
    }
}

fun NavController.navigateToWallpapersScreen(navOptions: NavOptions? = null) =
    navigate(WALLPAPERS_SCREEN, navOptions)

