package com.onthecrow.wallper.service

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultAllocator
import javax.inject.Inject

class ExoPlayerFactory @Inject constructor(
    private val watchhog: Watchhog,
) {
    fun create(context: Context, uri: String, startPosition: Long? = null, endPosition: Long? = null): ExoPlayer {
        return createExoPlayer(context).apply {
            watchhog.attachPlayer(this)
            setMediaItem(createMediaItem(uri, startPosition, endPosition))
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
            pauseAtEndOfMediaItems = false
            volume = 0f
        }
    }

    private fun createExoPlayer(context: Context): ExoPlayer {
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
            // Если у вашей версии есть этот метод — можно дополнительно «потолок» в байтах:
            // todo внимательно очень с этим
            .setTargetBufferBytes(16 * 1024 * 1024)
            .build()

        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }

    private fun createMediaItem(uri: String, startPosition: Long? = null, endPosition: Long? = null): MediaItem {
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder().apply {
            startPosition?.let { setStartPositionMs(it) }
            endPosition?.let { setEndPositionMs(it) }
        }
            .build()

        return MediaItem.Builder()
            .setUri(uri.toUri())
            .setClippingConfiguration(clippingConfiguration)
            .build()
    }
}