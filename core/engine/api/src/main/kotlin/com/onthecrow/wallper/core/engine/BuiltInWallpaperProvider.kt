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

    /** The OpenGL ES version [createSlide]'s renderer needs, 2 or 3: slides may share one context. */
    val glesVersion: Int get() = 2

    /**
     * The wallpaper as a slide a series prepares ahead: made off screen on [slide]'s GL host, shown later.
     * Null if it can only be made on the window, with [createEngine]. Called on the main thread.
     */
    fun createSlide(
        context: Context,
        surfaceHolder: () -> SurfaceHolder,
        isPreview: Boolean,
        host: EngineHost,
        slide: SlideStart,
    ): WallpaperEngine? = null

    /**
     * The wallpaper as a scene for a series' preview, at [glesVersion]: drawn at any moment of its own time.
     * [onChanged] is called, from any thread, when it would look different without being moved (what it
     * loads came). Null if it can't be shown that way: the preview leaves its slide empty. Main thread.
     */
    fun createScene(context: Context, onChanged: () -> Unit): BuiltInScene? = null

    /** What the system tints its UI with. Called off the main thread. */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun colors(): WallpaperColors

    /** See [BuiltInPreviews.previews]. */
    fun previews(): Flow<String>
}
