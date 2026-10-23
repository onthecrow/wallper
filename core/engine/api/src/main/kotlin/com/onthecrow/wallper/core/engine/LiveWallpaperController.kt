package com.onthecrow.wallper.core.engine

import android.content.Intent
import com.onthecrow.wallper.core.model.BuiltInWallpaper

/** What the UI may ask of the live wallpaper without knowing the service class. */
interface LiveWallpaperController {
    /** Opens the system live wallpaper preview with Wallper preselected. */
    fun createSetWallpaperIntent(): Intent

    /** Whether this device can draw [wallpaper]; the list hides it otherwise. */
    fun isSupported(wallpaper: BuiltInWallpaper): Boolean
}
