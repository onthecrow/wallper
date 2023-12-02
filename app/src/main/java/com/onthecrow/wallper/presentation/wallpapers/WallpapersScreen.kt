package com.onthecrow.wallper.presentation.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat.startActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onthecrow.wallper.core.actions.HandleUiActions
import com.onthecrow.wallper.presentation.wallpapers.models.WallpapersEvent
import com.onthecrow.wallper.service.WallperWallpaperService

@Composable
fun WallpapersScreen() {
    val viewModel = hiltViewModel<WallpapersViewModel>()
    val uiState = viewModel.uiState.collectAsState().value
    val context = LocalContext.current

    WallpapersUi(
        uiState = uiState,
        onWallpaperClick = { viewModel.sendEvent(WallpapersEvent.OnWallpaperClick(it)) },
        onSettingsClick = {
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

    HandleUiActions(viewModel) { _ ->
        TODO("Actions are not implemented yet")
    }
}

