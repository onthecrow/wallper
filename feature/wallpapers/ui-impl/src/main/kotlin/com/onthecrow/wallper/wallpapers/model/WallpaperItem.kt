package com.onthecrow.wallper.wallpapers.model

import com.onthecrow.wallper.core.model.BuiltInWallpaper

internal data class WallpaperItem(
    val id: Int,
    /** Null while there is nothing to show: a built-in's preview is still being rendered. */
    val picturePath: String?,
    val isActive: Boolean,
    /** Set for the wallpapers that ship with the app: their menu has settings instead of delete. */
    val builtIn: BuiltInWallpaper? = null,
)
