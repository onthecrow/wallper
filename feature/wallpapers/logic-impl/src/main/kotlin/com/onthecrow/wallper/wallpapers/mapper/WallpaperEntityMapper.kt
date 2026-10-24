package com.onthecrow.wallper.wallpapers.mapper

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper

/** Null for a built-in this app version doesn't know, or a row without media: such rows are skipped. */
internal fun WallpaperEntity.toDomain(): Wallpaper? = when {
    builtIn != null -> BuiltInWallpaper.fromKey(builtIn)?.let { wallpaper ->
        Wallpaper.BuiltIn(id = id, wallpaper = wallpaper, isActive = isActive)
    }
    // Rows from before v4 keep the original too, but a processed video is final.
    processedPath != null -> Wallpaper.ProcessedVideo(
        id = id,
        path = processedPath,
        thumbnailPath = thumbnailPath,
        isActive = isActive,
    )
    originalPath != null -> Wallpaper.Media(
        id = id,
        type = type,
        originalPath = originalPath,
        thumbnailPath = thumbnailPath,
        crop = CropRect(cropLeft, cropTop, cropRight, cropBottom),
        clip = ClipRange(clipStartMs, clipEndMs),
        mediaInfo = MediaInfo(mediaWidth, mediaHeight, mediaRotation, mediaDurationMs),
        isActive = isActive,
    )
    else -> null
}

/** A new row, or with [id] the new version of one. */
internal fun NewWallpaper.toEntity(id: Int = 0, isActive: Boolean = false) = when (this) {
    is NewWallpaper.Media -> WallpaperEntity(
        id = id,
        type = type,
        originalPath = originalPath,
        processedPath = null,
        thumbnailPath = thumbnailPath,
        cropLeft = crop.left,
        cropTop = crop.top,
        cropRight = crop.right,
        cropBottom = crop.bottom,
        clipStartMs = clip.startMs,
        clipEndMs = clip.endMs,
        mediaWidth = mediaInfo.width,
        mediaHeight = mediaInfo.height,
        mediaRotation = mediaInfo.rotation,
        mediaDurationMs = mediaInfo.durationMs,
        isActive = isActive,
    )
    is NewWallpaper.ProcessedVideo -> WallpaperEntity(
        id = id,
        type = MediaType.VIDEO,
        originalPath = null,
        processedPath = path,
        thumbnailPath = thumbnailPath,
        cropLeft = 0,
        cropTop = 0,
        cropRight = 0,
        cropBottom = 0,
        clipStartMs = 0,
        clipEndMs = 0,
        mediaWidth = 0,
        mediaHeight = 0,
        mediaRotation = 0,
        mediaDurationMs = 0,
        isActive = isActive,
    )
}

internal fun Wallpaper.toSpec(): WallpaperSpec = when (this) {
    is Wallpaper.Media -> WallpaperSpec.Media(
        type = type,
        sourcePath = originalPath,
        thumbnailPath = thumbnailPath,
        crop = crop,
        clip = clip,
        mediaInfo = mediaInfo,
    )
    is Wallpaper.ProcessedVideo -> WallpaperSpec.ProcessedVideo(path = path, thumbnailPath = thumbnailPath)
    is Wallpaper.BuiltIn -> WallpaperSpec.BuiltIn(wallpaper)
}
