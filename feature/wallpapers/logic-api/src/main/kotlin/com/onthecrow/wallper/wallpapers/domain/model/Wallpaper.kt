package com.onthecrow.wallper.wallpapers.domain.model

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.model.SlideTransition
import com.onthecrow.wallper.core.model.Tint

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
        /** What it is seen through while it is drawn, and its picture wherever that is shown; null for none. */
        val tint: Tint? = null,
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
        /**
         * The tint the export put into the video's frames. The thumbnail is the frame before it: whoever
         * shows the thumbnail adds the tint to it.
         */
        val tint: Tint? = null,
    ) : Wallpaper

    /** Ships with the app: it has settings instead of media and can't be deleted. */
    data class BuiltIn(
        override val id: Int,
        val wallpaper: BuiltInWallpaper,
        override val isActive: Boolean,
    ) : Wallpaper

    /**
     * A series of other wallpapers, never of groups: each of [members], in their order, shows for
     * [slideDuration], the next one coming in through [transition]. Deleting it leaves them; a member deleted
     * so that fewer than [MIN_MEMBERS] are left deletes it.
     */
    data class Group(
        override val id: Int,
        val members: List<Wallpaper>,
        val slideDuration: SlideDuration,
        override val isActive: Boolean,
        val transition: SlideTransition = SlideTransition.NONE,
    ) : Wallpaper {
        companion object {
            /** A series of one wallpaper would be that wallpaper. */
            const val MIN_MEMBERS = 2
        }
    }
}
