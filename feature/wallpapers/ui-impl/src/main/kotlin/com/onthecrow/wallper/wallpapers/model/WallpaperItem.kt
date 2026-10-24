package com.onthecrow.wallper.wallpapers.model

import com.onthecrow.wallper.core.model.BuiltInWallpaper

internal data class WallpaperItem(
    val id: Int,
    /** Null while there is nothing to show: a built-in's preview is still being rendered. */
    val picturePath: String?,
    val isActive: Boolean,
    /** Set for the wallpapers that ship with the app: they have settings instead of media, and stay. */
    val builtIn: BuiltInWallpaper? = null,
    /** The user's video kept only as an optimised export: it can't be edited, only deleted. */
    val isOptimised: Boolean = false,
)
