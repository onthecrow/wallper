package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** Everything the live wallpaper needs to render the active wallpaper. All paths are in device-protected storage. */
sealed interface WallpaperSpec {

    /** The user's media as it was picked, cropped on the GPU and trimmed while it plays. */
    data class Media(
        val type: MediaType,
        val sourcePath: String,
        val thumbnailPath: String,
        val crop: CropRect,
        val clip: ClipRange,
        val mediaInfo: MediaInfo,
    ) : WallpaperSpec

    /** A video the editor already cropped and trimmed: played as is. */
    data class ProcessedVideo(
        val path: String,
        val thumbnailPath: String,
    ) : WallpaperSpec

    /** A wallpaper rendered by the app itself. */
    data class BuiltIn(val wallpaper: BuiltInWallpaper) : WallpaperSpec
}
