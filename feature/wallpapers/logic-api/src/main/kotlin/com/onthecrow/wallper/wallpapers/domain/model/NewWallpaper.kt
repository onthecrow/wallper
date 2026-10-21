package com.onthecrow.wallper.wallpapers.domain.model

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** A wallpaper whose files are already in permanent storage, but which has no record yet. */
data class NewWallpaper(
    val type: MediaType,
    val originalPath: String,
    val processedPath: String?,
    val thumbnailPath: String,
    val crop: CropRect,
    val clip: ClipRange,
    val mediaInfo: MediaInfo,
)
