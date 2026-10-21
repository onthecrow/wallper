package com.onthecrow.wallper.core.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import javax.inject.Inject

/** Players for the wallpaper service: lean, clipped to the chosen range and watched by [Watchhog]. */
@UnstableApi
class ExoPlayerFactory @Inject constructor(
    private val watchhog: Watchhog,
) {
    fun create(context: Context, uri: String, startPosition: Long? = null, endPosition: Long? = null): ExoPlayer {
        return createLeanExoPlayer(context).apply {
            watchhog.attachPlayer(this)
            setMediaItem(createMediaItem(uri, startPosition, endPosition))
        }
    }

    private fun createMediaItem(uri: String, startPosition: Long?, endPosition: Long?): MediaItem {
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
