package com.onthecrow.wallper.core.player

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.onthecrow.wallper.core.player.engine.LeadInRenderersFactory
import com.onthecrow.wallper.core.player.engine.LeadInVideoRenderer

/**
 * A silent, looping ExoPlayer with a small buffer. The only place that builds ExoPlayer:
 * the wallpaper service and the editor preview both start from here.
 *
 * The audio track is disabled, not just muted: a muted track is still decoded into an AudioTrack,
 * which costs battery and becomes the playback clock.
 *
 * @param looper the thread all calls to the player must come from; the calling thread when null.
 * @param clipStartMs where the clip of the media starts, for an item that is set to start on a key frame
 * whether it does or not (`ClippingConfiguration.startsAtKeyFrame`): the player then keeps the frames
 * before the start off the screen ([LeadInVideoRenderer]). 0 for any other item.
 */
@OptIn(UnstableApi::class) // the allocator, load control tuning, decoder fallback and the renderer
fun createLeanExoPlayer(context: Context, looper: Looper? = null, clipStartMs: Long = 0): ExoPlayer {
    // 64 KB segments, trimmed on reset
    val allocator = DefaultAllocator(/* trimOnReset = */ true,
        C.DEFAULT_BUFFER_SEGMENT_SIZE
    )

    val loadControl = DefaultLoadControl.Builder()
        // 1.5–5 s of buffer is plenty for a looping wallpaper clip
        .setBufferDurationsMs(
            /* minBufferMs = */ 1500,
            /* maxBufferMs = */ 5000,
            /* bufferForPlaybackMs = */ 250,
            /* bufferForPlaybackAfterRebufferMs = */ 500
        )
        // Time over size: never buffer more than the durations above ask for
        .setPrioritizeTimeOverSizeThresholds(true)
        .setAllocator(allocator)
        // TODO Check the cap against high-bitrate clips: it bounds the buffer however long it is.
        .setTargetBufferBytes(16 * 1024 * 1024)
        .build()

    // If the preferred decoder fails to initialize, try the next one instead of failing playback.
    val renderersFactory = if (clipStartMs > 0) {
        LeadInRenderersFactory(context, clipStartUs = clipStartMs * 1_000)
    } else {
        DefaultRenderersFactory(context)
    }
        .setEnableDecoderFallback(true)

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
