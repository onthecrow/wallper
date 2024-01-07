package com.onthecrow.wallper.service.renderer

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.service.VideoMetadata

sealed class RendererParams(
    open val width: Int,
    open val height: Int,
    open val player: ExoPlayer? = null,
    open val rect: Rect = Rect(0, 0, width, height),
    open val videoMetadata: VideoMetadata = VideoMetadata(0, width, height)
) {
    data class PlaceholderParams(override val width: Int, override val height: Int) :
        RendererParams(width, height)

    data class VideoParams(
        override val width: Int,
        override val height: Int,
        override val player: ExoPlayer,
        override val rect: Rect,
        override val videoMetadata: VideoMetadata,
    ) : RendererParams(width, height, player, rect, videoMetadata)

    data class PictureParams(
        override val width: Int,
        override val height: Int,
        val bitmap: Bitmap,
    ) : RendererParams(width, height)
}