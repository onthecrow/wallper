package com.onthecrow.wallper.core.engine

import android.content.Intent

/** What the UI may ask of the live wallpaper without knowing the service class. */
interface LiveWallpaperController {
    /** Opens the system live wallpaper preview with Wallper preselected. */
    fun createSetWallpaperIntent(): Intent
}
