package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.BuiltInWallpaper

/** Still previews of built-in wallpapers for the list, rendered by the same code as the wallpaper. */
interface BuiltInPreviews {
    /**
     * Path to a PNG of [wallpaper] in device-protected storage. Rendered on the first call (it may take
     * a few seconds) and cached after that. Null if this device can't render it.
     */
    suspend fun preview(wallpaper: BuiltInWallpaper): String?
}
