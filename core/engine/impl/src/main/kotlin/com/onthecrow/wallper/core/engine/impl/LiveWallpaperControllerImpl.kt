package com.onthecrow.wallper.core.engine.impl

import android.app.ActivityManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class LiveWallpaperControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LiveWallpaperController {

    private val glEsVersion by lazy {
        context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
    }

    override fun createSetWallpaperIntent(): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, WallperWallpaperService::class.java),
        )

    override fun isSupported(wallpaper: BuiltInWallpaper): Boolean = when (wallpaper) {
        BuiltInWallpaper.BLACK_HOLE -> glEsVersion >= GLES_3_0
    }

    private companion object {
        const val GLES_3_0 = 0x30000
    }
}
