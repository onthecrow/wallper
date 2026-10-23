package com.onthecrow.wallper.core.engine

import android.content.Intent
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import kotlinx.coroutines.flow.Flow

/** What the UI may ask of the live wallpaper without knowing the service class. */
interface LiveWallpaperController {
    /** Opens the system live wallpaper preview with Wallper preselected. */
    fun createSetWallpaperIntent(): Intent

    /** Whether this device can draw [wallpaper]; the list hides it otherwise. */
    fun isSupported(wallpaper: BuiltInWallpaper): Boolean

    /** Whether Wallper is the home screen's wallpaper now. A binder call: not on the main thread. */
    fun isWallpaperSet(): Boolean

    /**
     * Emits when the device's wallpaper may have changed, ours or any other: ask [isWallpaperSet] again.
     * Nothing comes below API 27; check again when the screen returns instead.
     */
    fun wallpaperChanges(): Flow<Unit>
}
