package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import kotlinx.coroutines.flow.Flow

/** Still previews of built-in wallpapers for the list, rendered by the same code as the wallpaper. */
interface BuiltInPreviews {
    /**
     * Paths to PNGs of [wallpaper] in device-protected storage: one now, and a new one whenever its
     * settings change the picture. A render takes seconds and is cached across launches. Nothing comes
     * if this device can't render it.
     */
    fun previews(wallpaper: BuiltInWallpaper): Flow<String>
}
