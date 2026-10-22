package com.onthecrow.wallper.core.player

import android.content.Context
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject

/** Players for the wallpaper service: lean, clipped to the chosen range, owned by the engine's player thread. */
class ExoPlayerFactory @Inject constructor() {

    fun create(
        context: Context,
        uri: String,
        looper: Looper,
        startPositionMs: Long? = null,
        endPositionMs: Long? = null,
    ): ExoPlayer {
        return createLeanExoPlayer(context, looper).apply {
            setMediaItem(createMediaItem(uri, startPositionMs, endPositionMs))
        }
    }

    private fun createMediaItem(uri: String, startPositionMs: Long?, endPositionMs: Long?): MediaItem {
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder().apply {
            startPositionMs?.let { setStartPositionMs(it) }
            endPositionMs?.let { setEndPositionMs(it) }
        }
            .build()

        return MediaItem.Builder()
            .setUri(uri.toUri())
            .setClippingConfiguration(clippingConfiguration)
            .build()
    }
}
