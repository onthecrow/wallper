package com.onthecrow.wallper.core.engine.impl

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class LiveWallpaperControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LiveWallpaperController {

    override fun createSetWallpaperIntent(): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, WallperWallpaperService::class.java),
        )
}
