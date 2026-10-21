package com.onthecrow.wallper.core.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * A silent, looping ExoPlayer with a small buffer. The only place that builds ExoPlayer:
 * the wallpaper service and the editor preview both start from here.
 */
fun createLeanExoPlayer(context: Context): ExoPlayer {
    // 64КБ сегменты, тримим при reset’e
    val allocator = DefaultAllocator(/* trimOnReset = */ true,
        C.DEFAULT_BUFFER_SEGMENT_SIZE
    )

    val loadControl = DefaultLoadControl.Builder()
        // 1.5–5 c буфера обычно более чем достаточно для обоев
        .setBufferDurationsMs(
            /* minBufferMs = */ 1500,
            /* maxBufferMs = */ 5000,
            /* bufferForPlaybackMs = */ 250,
            /* bufferForPlaybackAfterRebufferMs = */ 500
        )
        // Приоритет «времени» над размером: не раздувать буфер сверх нужного времени
        .setPrioritizeTimeOverSizeThresholds(true)
        .setAllocator(allocator)
        // todo внимательно очень с этим
        .setTargetBufferBytes(16 * 1024 * 1024)
        .build()

    return ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .build()
        .apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            pauseAtEndOfMediaItems = false
            volume = 0f
        }
}
