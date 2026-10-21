package com.onthecrow.wallper.core.engine

import kotlinx.coroutines.flow.Flow

/** Port the wallpaper service reads from. Implemented by the feature that owns wallpapers. */
interface ActiveWallpaperSource {
    /** The wallpaper to show, or null for the branded placeholder. */
    val activeWallpaperFlow: Flow<WallpaperSpec?>
}
