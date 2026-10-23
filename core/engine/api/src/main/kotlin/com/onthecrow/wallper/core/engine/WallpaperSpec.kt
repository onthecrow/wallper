package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** Everything the live wallpaper needs to render the active wallpaper. */
sealed interface WallpaperSpec {

    /** A wallpaper made from the user's media. All paths are in device-protected storage. */
    data class Media(
        val type: MediaType,
        val sourcePath: String,
        /** Video already cropped by the editor; when present it is played as is, without GL cropping. */
        val processedPath: String?,
        val thumbnailPath: String,
        val crop: CropRect,
        val clip: ClipRange,
        val mediaInfo: MediaInfo,
    ) : WallpaperSpec

    /** A wallpaper rendered by the app itself. */
    data class BuiltIn(val wallpaper: BuiltInWallpaper) : WallpaperSpec
}
