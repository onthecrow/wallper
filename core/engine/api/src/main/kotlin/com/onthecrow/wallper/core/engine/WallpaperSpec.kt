package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.core.model.SlideTransition
import com.onthecrow.wallper.core.model.Tint

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
        /** What the renderer shows every frame through; null for none. */
        val tint: Tint? = null,
    ) : WallpaperSpec

    /**
     * A video the editor already cropped and trimmed: played as is. Its [tint] is in its frames already and
     * is never drawn; it is here for the system's colours, which come from the thumbnail, the frame before it.
     */
    data class ProcessedVideo(
        val path: String,
        val thumbnailPath: String,
        val tint: Tint? = null,
    ) : WallpaperSpec

    /** A wallpaper rendered by the app itself. */
    data class BuiltIn(val wallpaper: BuiltInWallpaper) : WallpaperSpec

    /**
     * The user's group: [slides] in turn, each for [slideDuration], the next one coming in through
     * [transition] where the slides share a GL thread (it cuts elsewhere). A slide is never a series itself.
     * The system's colours follow the first slide only: on Android 12+ each change re-themes the launcher and
     * apps.
     */
    data class Series(
        val slides: List<WallpaperSpec>,
        val slideDuration: SlideDuration,
        val transition: SlideTransition = SlideTransition.NONE,
    ) : WallpaperSpec
}
