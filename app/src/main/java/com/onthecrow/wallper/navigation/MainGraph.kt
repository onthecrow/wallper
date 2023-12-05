package com.onthecrow.wallper.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.navigation
import com.onthecrow.wallper.presentation.crop.cropperScreen
import com.onthecrow.wallper.presentation.wallpapers.WALLPAPERS_SCREEN
import com.onthecrow.wallper.presentation.wallpapers.wallpapersScreen

const val MAIN_GRAPH = "MainGraph"

fun NavGraphBuilder.mainGraph() {
    navigation(
        startDestination = WALLPAPERS_SCREEN,
        route = MAIN_GRAPH
    ) {
        wallpapersScreen()
        cropperScreen()
    }
}

fun NavController.navigateToMainGraph(navOptions: NavOptions) = navigate(MAIN_GRAPH, navOptions)
