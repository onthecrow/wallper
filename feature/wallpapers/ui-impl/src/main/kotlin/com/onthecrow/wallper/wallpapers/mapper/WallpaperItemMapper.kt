package com.onthecrow.wallper.wallpapers.mapper

import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.wallpapers.domain.MakeSeriesPictureUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal fun Wallpaper.toItem(): WallpaperItem = when (this) {
    is Wallpaper.Media -> WallpaperItem(
        id = id,
        picturePath = if (type == MediaType.VIDEO) thumbnailPath else originalPath,
        isActive = isActive,
    )
    is Wallpaper.ProcessedVideo -> WallpaperItem(
        id = id,
        picturePath = thumbnailPath,
        isActive = isActive,
        isOptimised = true,
    )
    // The picture is the preview: it is rendered in the background and added by the reducer.
    is Wallpaper.BuiltIn -> WallpaperItem(
        id = id,
        picturePath = null,
        isActive = isActive,
        builtIn = wallpaper,
    )
    // The picture is made of the cover's in the background, and added by the reducer.
    is Wallpaper.Group -> WallpaperItem(
        id = id,
        picturePath = null,
        isActive = isActive,
        isGroup = true,
        cover = members.take(MakeSeriesPictureUseCase.MAX_PICTURES).map { it.toItem() },
    )
}
