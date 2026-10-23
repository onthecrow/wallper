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

    /** Made by the user from their media in the editor. */
    data class Media(
        override val id: Int,
        val type: MediaType,
        val originalPath: String,
        /** Video pre-cropped by the editor ("additional processing"), null when cropping happens in GL. */
        val processedPath: String?,
        val thumbnailPath: String,
        val crop: CropRect,
        val clip: ClipRange,
        val mediaInfo: MediaInfo,
        override val isActive: Boolean,
    ) : Wallpaper

    /** Ships with the app: it has settings instead of media and can't be deleted. */
    data class BuiltIn(
        override val id: Int,
        val wallpaper: BuiltInWallpaper,
        override val isActive: Boolean,
    ) : Wallpaper
}
