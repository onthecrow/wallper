package com.onthecrow.wallper.core.engine

import android.app.WallpaperColors
import android.content.Context
import android.os.Build
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import kotlinx.coroutines.flow.Flow

/**
 * One built-in wallpaper, bound by its feature with `@IntoSet`. The service, the list and the system
 * picker know built-ins only through this; a key without a provider shows the placeholder.
 */
interface BuiltInWallpaperProvider {
    val wallpaper: BuiltInWallpaper

    /** Whether this device can draw it; the list hides it otherwise. */
    fun isSupported(): Boolean

    /**
     * Called on the main thread.
     * @param isPreview the system picker's preview: it may render cheaper there.
     */
    fun createEngine(
        context: Context,
        surfaceHolder: () -> SurfaceHolder,
        isPreview: Boolean,
        host: EngineHost,
    ): WallpaperEngine

    /** What the system tints its UI with. Called off the main thread. */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun colors(): WallpaperColors

    /** See [BuiltInPreviews.previews]. */
    fun previews(): Flow<String>
}
