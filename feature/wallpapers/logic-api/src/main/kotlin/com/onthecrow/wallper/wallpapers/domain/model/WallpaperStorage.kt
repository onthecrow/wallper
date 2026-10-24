package com.onthecrow.wallper.wallpapers.domain.model

/** A user's [wallpaper], [Wallpaper.Media] or [Wallpaper.ProcessedVideo], and the bytes its files take. */
data class WallpaperStorage(
    val wallpaper: Wallpaper,
    val bytes: Long,
)
