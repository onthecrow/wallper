package com.onthecrow.wallper.wallpapers.domain.model

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** A row of the wallpapers list; any of them can be the active one. */
sealed interface Wallpaper {
    val id: Int
    val isActive: Boolean

    /**
     * The user's media as it was picked: cropped and trimmed while it plays. The original is kept, so it can
     * be edited again.
     */
    data class Media(
        override val id: Int,
        val type: MediaType,
        val originalPath: String,
        val thumbnailPath: String,
        val crop: CropRect,
        val clip: ClipRange,
        val mediaInfo: MediaInfo,
        override val isActive: Boolean,
    ) : Wallpaper

    /**
     * A video the editor exported already cropped and trimmed ("additional processing"): it plays as is.
     * Nothing else is kept, so it can't be edited, only deleted.
     */
    data class ProcessedVideo(
        override val id: Int,
        val path: String,
        val thumbnailPath: String,
        override val isActive: Boolean,
    ) : Wallpaper

    /** Ships with the app: it has settings instead of media and can't be deleted. */
    data class BuiltIn(
        override val id: Int,
        val wallpaper: BuiltInWallpaper,
        override val isActive: Boolean,
    ) : Wallpaper
}
