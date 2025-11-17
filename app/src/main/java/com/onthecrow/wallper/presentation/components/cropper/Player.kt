package com.onthecrow.wallper.presentation.components.cropper

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.activity.addCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultAllocator

@Composable
fun VideoSurface(
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
                volume = 0f
            }
    }

    // Отдельно реагируем на смену URI (без пересоздания плеера)
    LaunchedEffect(videoUri) {
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUri.toUri()))

        player.setMediaSource(mediaSource)
        player.prepare()
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        player.playWhenReady = true
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