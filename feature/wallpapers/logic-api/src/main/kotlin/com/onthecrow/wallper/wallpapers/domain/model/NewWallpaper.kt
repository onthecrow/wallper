package com.onthecrow.wallper.wallpapers.domain.model

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** What the editor made: its files are already in permanent storage, but no record shows them yet. */
sealed interface NewWallpaper {
    val thumbnailPath: String

    /** See [Wallpaper.Media]. */
    data class Media(
        val type: MediaType,
        val originalPath: String,
        override val thumbnailPath: String,
        val crop: CropRect,
        val clip: ClipRange,
        val mediaInfo: MediaInfo,
    ) : NewWallpaper

    /** See [Wallpaper.ProcessedVideo]. */
    data class ProcessedVideo(
        val path: String,
        override val thumbnailPath: String,
    ) : NewWallpaper
}
