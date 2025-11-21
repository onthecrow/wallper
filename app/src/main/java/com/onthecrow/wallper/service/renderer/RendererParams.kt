package com.onthecrow.wallper.service.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.service.VideoMetadata

sealed class RendererParams(
    open val width: Int = 3840,
    open val height: Int = 2160,
    open val player: ExoPlayer? = null,
    open val rect: Rect = Rect(0, 0, width, height),
    open val videoMetadata: VideoMetadata = VideoMetadata(
        0,
        width,
        height,
        0L,
    )
) {
    data class PlaceholderParams(
        override val width: Int = 3840,
        override val height: Int = 2160,
        ) :
        RendererParams(width, height)

    data class VideoParams(
        override val width: Int = 3840,
        override val height: Int = 2160,
        override val player: ExoPlayer,
        override val rect: Rect = Rect(0, 0, width, height),
        override val videoMetadata: VideoMetadata = VideoMetadata(
            0,
            width,
            height,
            0L,
        ),
    ) : RendererParams(width, height, player, rect, videoMetadata)

    data class PictureParams(
        override val width: Int,
        override val height: Int,
        val bitmap: Bitmap,
    ) : RendererParams(width, height)
}