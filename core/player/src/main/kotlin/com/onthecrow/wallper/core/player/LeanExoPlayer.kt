package com.onthecrow.wallper.core.player

import android.content.Context
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * A silent, looping ExoPlayer with a small buffer. The only place that builds ExoPlayer:
 * the wallpaper service and the editor preview both start from here.
 *
 * The audio track is disabled, not just muted: a muted track is still decoded into an AudioTrack,
 * which costs battery and becomes the playback clock.
 *
 * @param looper the thread all calls to the player must come from; the calling thread when null.
 */
fun createLeanExoPlayer(context: Context, looper: Looper? = null): ExoPlayer {
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

    // If the preferred decoder fails to initialize, try the next one instead of failing playback.
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)

    return ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .apply { looper?.let(::setLooper) }
        .build()
        .apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            pauseAtEndOfMediaItems = false
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
        }
}
