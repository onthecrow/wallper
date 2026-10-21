package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** Everything the live wallpaper needs to render the active wallpaper. All paths are in device-protected storage. */
data class WallpaperSpec(
    val type: MediaType,
    val sourcePath: String,
    /** Video already cropped by the editor; when present it is played as is, without GL cropping. */
    val processedPath: String?,
    val thumbnailPath: String,
    val crop: CropRect,
    val clip: ClipRange,
    val mediaInfo: MediaInfo,
)
