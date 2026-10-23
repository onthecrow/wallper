package com.onthecrow.wallper.editor.cropper

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import com.onthecrow.wallper.core.player.createLeanExoPlayer

/**
 * Plays the picked part of [videoUri] in a loop.
 *
 * @param durationMs the length of the video from its metadata; 0 if unknown, then the whole video plays.
 */
@Composable
fun VideoSurface(
    videoUri: String,
    durationMs: Long,
    modifier: Modifier = Modifier,
    seek: Float?,
    videoRange: ClosedFloatingPointRange<Float>,
) {
    val context = LocalContext.current

    // Плеер живёт столько, сколько живёт этот composable
    val player = remember(context) { createLeanExoPlayer(context) }

    LaunchedEffect(videoUri, durationMs, videoRange, seek) {
        if (seek != null) return@LaunchedEffect
        player.setMediaItem(clippedMediaItem(videoUri, durationMs, videoRange))
        player.prepare()
        player.play()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                player.setVideoTextureView(this)
            }
        },
        update = { view ->
            player.setVideoTextureView(view)
        },
        onReset = { view ->
            player.setVideoTextureView(null)
            view.surfaceTextureListener = null
        },
        onRelease = { view ->
            player.setVideoTextureView(null)
            view.surfaceTextureListener = null
            player.stop()
            player.release()
        }
    )
}

/** The picked part of the video; the whole of it while the duration is unknown. */
private fun clippedMediaItem(videoUri: String, durationMs: Long, range: ClosedFloatingPointRange<Float>): MediaItem {
    val builder = MediaItem.Builder().setUri(videoUri.toUri())
    if (durationMs > 0) {
        val startMs = (range.start.coerceIn(0f, 1f) * durationMs).toLong()
        val endMs = (range.endInclusive.coerceIn(0f, 1f) * durationMs).toLong().coerceAtLeast(startMs)
        builder.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build(),
        )
    }
    return builder.build()
}
