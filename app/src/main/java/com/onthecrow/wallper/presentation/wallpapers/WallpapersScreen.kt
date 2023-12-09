package com.onthecrow.wallper.presentation.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.onthecrow.wallper.core.actions.HandleUiActions
import com.onthecrow.wallper.navigation.LocalNavController
import com.onthecrow.wallper.presentation.crop.navigateToCropperScreen
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersAction
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersEvent
import com.onthecrow.wallper.service.WallperWallpaperService

@Composable
fun WallpapersScreen() {
    val viewModel = hiltViewModel<WallpapersViewModel>()
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    val navController = LocalNavController.current

    WallpapersUi(
        uiState = uiState,
        onWallpaperClick = { viewModel.sendEvent(WallpapersEvent.OnWallpaperClick(it)) },
        onSettingsClick = {
            // TODO move it somewhere else
            val intent = Intent(
                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            )
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(context, WallperWallpaperService::class.java)
            )
            context.startActivity(intent)
        },
        onAddClick = { viewModel.addWallpaper() },
    )

    HandleUiActions(viewModel) { action ->
        when (action) {
            is WallpapersAction.NavigateToCropper -> navController.navigateToCropperScreen(action.uri)
        }
    }
}
