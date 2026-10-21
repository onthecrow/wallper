package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo

/** Surface size in pixels plus the part of the video to show on it. */
data class VideoRenderParams(
    val width: Int,
    val height: Int,
    val crop: CropRect,
    val mediaInfo: MediaInfo,
)
