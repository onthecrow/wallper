package com.onthecrow.wallper.presentation.components.cropper

import android.os.Looper
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import timber.log.Timber

@Composable
fun VideoSurface(
    videoUri: String,
    modifier: Modifier = Modifier,
    seek: Float?,
    videoRange: ClosedFloatingPointRange<Float>,
) {
    val context = LocalContext.current
    var initialRange by remember { mutableStateOf(0L) }

    // Плеер живёт столько, сколько живёт этот composable (строка в LazyColumn)
    val player = remember(context) {
        val allocator = DefaultAllocator(
            /* trimOnReset = */ true,
            C.DEFAULT_BUFFER_SEGMENT_SIZE
        )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 5000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setAllocator(allocator)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                repeatMode = ExoPlayer.REPEAT_MODE_ONE
                pauseAtEndOfMediaItems = false
                volume = 0f
            }
    }

    LaunchedEffect(videoRange, seek) {
        if (seek != null) return@LaunchedEffect
        val startPosition = videoRange.start * initialRange
        val endPosition = videoRange.endInclusive * initialRange

        Timber.d("startPosition: $startPosition, endPosition: $endPosition")

        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startPosition.toLong())
            .setEndPositionMs(endPosition.toLong())
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri.toUri())
            .setClippingConfiguration(clippingConfiguration)
            .build()

        player.stop()
        player.setMediaItem(mediaItem)
//            player.setMediaSource(mediaSource)
        player.prepare()
        player.play()
    }

    // Отдельно реагируем на смену URI (без пересоздания плеера)
    LaunchedEffect(videoUri) {
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri.toUri())
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        android.os.Handler(Looper.getMainLooper()).postDelayed(
            {
                initialRange = player.duration
            }, 2000L
        )
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