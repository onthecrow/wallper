package com.onthecrow.wallper.core.engine.impl

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

internal class LiveWallpaperControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val builtIns: BuiltInWallpapers,
) : LiveWallpaperController {

    private val service = ComponentName(context, WallperWallpaperService::class.java)

    override fun createSetWallpaperIntent(): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            service,
        )

    override fun isSupported(wallpaper: BuiltInWallpaper): Boolean = builtIns[wallpaper]?.isSupported() == true

    override fun isWallpaperSet(): Boolean = WallpaperManager.getInstance(context).wallpaperInfo?.component == service

    // Any new wallpaper brings its colours, a live one once its engine is up: the system's own signal
    // of a change, with no binding to our service to keep alive.
    override fun wallpaperChanges(): Flow<Unit> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            awaitClose()
            return@callbackFlow
        }
        val manager = WallpaperManager.getInstance(context)
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> trySend(Unit) }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        awaitClose { manager.removeOnColorsChangedListener(listener) }
    }
}
