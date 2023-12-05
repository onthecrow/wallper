package com.onthecrow.wallper.presentation.crop

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val CROPPER_SCREEN_NAV_ARGUMENT_URI = "uri"
const val CROPPER_SCREEN_NAV_ARGUMENT_URI_PARENTHESIZED = "{$CROPPER_SCREEN_NAV_ARGUMENT_URI}"
const val CROPPER_SCREEN = "CropperScreen/%s"

fun NavGraphBuilder.cropperScreen() {
    composable(
        route = String.format(CROPPER_SCREEN, CROPPER_SCREEN_NAV_ARGUMENT_URI_PARENTHESIZED),
        arguments = listOf(navArgument(CROPPER_SCREEN_NAV_ARGUMENT_URI) { type = NavType.StringType }),
    ) {
        CropperScreen()
    }
}

fun NavController.navigateToCropperScreen(
    uri: String,
    navOptions: NavOptions? = null,
) = navigate(
    String.format(
        CROPPER_SCREEN,
        URLEncoder.encode(uri, StandardCharsets.UTF_8.toString())
    ),
    navOptions,
)
