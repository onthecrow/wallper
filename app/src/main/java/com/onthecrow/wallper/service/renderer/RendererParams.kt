package com.onthecrow.wallper.service.renderer

import android.graphics.Rect
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.service.VideoMetadata

sealed class RendererParams(
    val width: Int,
    val height: Int,
    val player: ExoPlayer? = null,
    val rect: Rect = Rect(0, 0, width, height),
    val videoMetadata: VideoMetadata = VideoMetadata(0, width, height)
) {
    class PlaceholderParams(width: Int, height: Int): RendererParams(width, height)
    class VideoParams(width: Int,
                      height: Int,
                      player: ExoPlayer,
                      rect: Rect,
                      videoMetadata: VideoMetadata,
    ): RendererParams(width, height, player, rect, videoMetadata)
}