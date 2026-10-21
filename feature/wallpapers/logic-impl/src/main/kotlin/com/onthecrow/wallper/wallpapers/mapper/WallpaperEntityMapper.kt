package com.onthecrow.wallper.wallpapers.mapper

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper

internal fun WallpaperEntity.toDomain() = Wallpaper(
    id = id,
    type = type,
    originalPath = originalPath,
    processedPath = processedPath,
    thumbnailPath = thumbnailPath,
    crop = CropRect(cropLeft, cropTop, cropRight, cropBottom),
    clip = ClipRange(clipStartMs, clipEndMs),
    mediaInfo = MediaInfo(mediaWidth, mediaHeight, mediaRotation, mediaDurationMs),
    isActive = isActive,
)

internal fun NewWallpaper.toEntity() = WallpaperEntity(
    type = type,
    originalPath = originalPath,
    processedPath = processedPath,
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
    isActive = false,
)

internal fun Wallpaper.toSpec() = WallpaperSpec(
    type = type,
    sourcePath = originalPath,
    processedPath = processedPath,
    thumbnailPath = thumbnailPath,
    crop = crop,
    clip = clip,
    mediaInfo = mediaInfo,
)
