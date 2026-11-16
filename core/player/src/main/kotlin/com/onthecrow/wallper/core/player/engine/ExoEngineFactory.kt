package com.onthecrow.wallper.core.player.engine

import android.content.Context
import android.os.Handler
import android.view.Surface
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.onthecrow.wallper.core.player.PlayerMedia
import com.onthecrow.wallper.core.player.createLeanExoPlayer

/**
 * Engines for [media]: lean, silent, clipped to end with its video ([loopEndUs]), living on [handler]'s
 * thread. A clip that starts into the file loops without a decoder flush, see [mediaItem].
 */
@UnstableApi
internal class ExoEngineFactory(
    private val context: Context,
    private val media: PlayerMedia,
    private val handler: Handler,
) : EngineFactory {

    private var nextId = 1

    private val clipStartMs: Long = media.clipStartMs ?: 0

    /** Read from the file once, on the player thread, with the first engine. */
    private val clipEndUs: Long? by lazy(LazyThreadSafetyMode.NONE) {
        loopEndUs(media.clipStartMs, media.clipEndMs, videoTrackEndUs(media.path))
    }

    override fun create(positionMs: Long, surface: Surface?, playWhenReady: Boolean): ManagedEngine {
        val player = createLeanExoPlayer(context, handler.looper, clipStartMs).apply {
            setMediaItem(mediaItem())
            surface?.let(::setVideoSurface)
            if (positionMs > 0) seekTo(positionMs)
            prepare()
            this.playWhenReady = playWhenReady
        }
        return ExoPlayerEngine(nextId++, player)
    }

    /**
     * A clip that starts into the file is said to start on a key frame, whether it does or not. Media3
     * would otherwise start every loop of it with a discontinuity: play the decoder out, flush it and
     * decode again from the key frame before the start, holding the loop's last frame meanwhile and
     * dropping the loop's first ones for being late. Said to start on a key frame, the clip loops in one
     * decoder session, and the frames up to its start are the player's to keep off the screen: it is
     * made with the clip's start for that ([LeadInVideoRenderer]).
     */
    private fun mediaItem(): MediaItem {
        val clippingConfiguration = MediaItem.ClippingConfiguration.Builder().apply {
            if (clipStartMs > 0) {
                setStartPositionMs(clipStartMs)
                setStartsAtKeyFrame(true)
            }
            clipEndUs?.let { setEndPositionUs(it) }
        }
            .build()

        return MediaItem.Builder()
            .setUri(media.path.toUri())
            .setClippingConfiguration(clippingConfiguration)
            .build()
    }
}
