package com.onthecrow.wallper.wallpapers.domain.model

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

data class Wallpaper(
    val id: Int,
    val type: MediaType,
    val originalPath: String,
    /** Video pre-cropped by the editor ("additional processing"), null when cropping happens in GL. */
    val processedPath: String?,
    val thumbnailPath: String,
    val crop: CropRect,
    val clip: ClipRange,
    val mediaInfo: MediaInfo,
    val isActive: Boolean,
)
