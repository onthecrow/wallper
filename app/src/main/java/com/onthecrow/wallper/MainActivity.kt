package com.onthecrow.wallper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import com.onthecrow.wallper.presentation.MainScreen
import com.onthecrow.wallper.presentation.wallpaperlist.WallpaperListViewModel
import com.onthecrow.wallper.service.WallperWallpaperService
import com.onthecrow.wallper.ui.theme.WallperTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val onSettingsClick = {
            val intent = Intent(
                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
            )
            intent.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, WallperWallpaperService::class.java)
            )
            startActivity(intent)
        }
        setContent {
            WallperTheme {
                val wallpaperListViewModel by viewModels<WallpaperListViewModel>()
                // A surface container using the 'background' color from the theme
                MainScreen(
                    wallpaperListViewModel.state.collectAsState(),
                    {},
                    onSettingsClick = onSettingsClick,
                    onItemClick = { wallpaperListViewModel.activateWallpaper(it) }
                )
            }
        }
    }
}
