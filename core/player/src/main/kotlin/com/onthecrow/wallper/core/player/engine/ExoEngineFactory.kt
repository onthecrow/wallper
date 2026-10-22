package com.onthecrow.wallper.core.player.engine

import android.content.Context
import android.os.Handler
import android.view.Surface
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.onthecrow.wallper.core.player.PlayerMedia
import com.onthecrow.wallper.core.player.createLeanExoPlayer

/** Engines for [media]: lean, silent, clipped, living on [handler]'s thread. */
@UnstableApi
internal class ExoEngineFactory(
    private val context: Context,
    private val media: PlayerMedia,
    private val handler: Handler,
) : EngineFactory {

    private var nextId = 1

    override fun create(positionMs: Long, surface: Surface?, playWhenReady: Boolean): ManagedEngine {
        val player = createLeanExoPlayer(context, handler.looper).apply {
            setMediaItem(mediaItem())
            surface?.let(::setVideoSurface)
            if (positionMs > 0) seekTo(positionMs)
            prepare()
            this.playWhenReady = playWhenReady
        }
        return ExoPlayerEngine(nextId++, player)
    }

    private fun mediaItem(): MediaItem {
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder().apply {
            media.clipStartMs?.let { setStartPositionMs(it) }
            media.clipEndMs?.let { setEndPositionMs(it) }
        }
            .build()

        return MediaItem.Builder()
            .setUri(media.path.toUri())
            .setClippingConfiguration(clippingConfiguration)
            .build()
    }
}
