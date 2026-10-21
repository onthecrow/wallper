package com.onthecrow.wallper.wallpapers.mapper

import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal fun Wallpaper.toItem() = WallpaperItem(
    id = id,
    picturePath = if (type == MediaType.VIDEO) thumbnailPath else originalPath,
    isActive = isActive,
)
