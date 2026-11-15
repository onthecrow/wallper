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
        tint = tint,
    )
    is Wallpaper.ProcessedVideo -> WallpaperItem(
        id = id,
        picturePath = thumbnailPath,
        isActive = isActive,
        isOptimised = true,
        // In the video's frames, but not in its thumbnail.
        tint = tint,
    )
    // The picture is the preview: it is rendered in the background and added by the reducer.
    is Wallpaper.BuiltIn -> WallpaperItem(
        id = id,
        picturePath = null,
        isActive = isActive,
        builtIn = wallpaper,
    )
    // The picture is made of the cover's in the background, and added by the reducer.
    is Wallpaper.Group -> {
        val cover = members.take(MakeSeriesPictureUseCase.MAX_PICTURES).map { it.toItem() }
        WallpaperItem(
            id = id,
            picturePath = null,
            isActive = isActive,
            isGroup = true,
            cover = cover,
            // A series of one shows its member's own picture, which has no tint in it.
            tint = cover.singleOrNull()?.tint,
        )
    }
}
