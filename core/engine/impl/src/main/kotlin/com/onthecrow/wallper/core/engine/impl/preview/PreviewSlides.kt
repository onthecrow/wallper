package com.onthecrow.wallper.core.engine.impl.preview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.core.engine.BuiltInScene
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.gl.GLWallpaperRenderer
import com.onthecrow.wallper.core.gl.PictureWallpaperRenderer
import com.onthecrow.wallper.core.gl.VideoRenderParams
import com.onthecrow.wallper.core.gl.VideoWallpaperRenderer
import com.onthecrow.wallper.core.player.createLeanExoPlayer
import timber.log.Timber
import java.util.concurrent.Executor

/**
 * One slide of a series' preview: the wallpaper's own renderer, and what moves it in its own time. Main
 * thread only; the renderer is the preview's GL thread's.
 */
internal interface PreviewSlide {
    /** What draws it; null for a slide that can't be shown, which stays empty. */
    val renderer: GLWallpaperRenderer?

    /**
     * Puts the slide [sinceShownMs] into its own time, playing on from there or standing still. Without
     * [seek] only that changes: a playing slide is already where the clock is.
     */
    fun moveTo(sinceShownMs: Long, playing: Boolean, seek: Boolean)

    /** Many seeks are coming: the finger is on the timeline. */
    fun setScrubbing(scrubbing: Boolean) = Unit

    /** Lets go of what it holds; its renderer goes with the GL thread's [SeriesPreviewImpl.drop]. */
    fun release()
}

/**
 * A video slide: an ExoPlayer of its own, not the wallpaper's player, playing the clip round and round into
 * the texture of the wallpaper's [VideoWallpaperRenderer], which crops, turns and tints it as on the home
 * screen. Seeks show the frame there even while it stands still, and a finger on the timeline puts the player
 * in its scrubbing mode, as on the editor's trim bar.
 *
 * @param wallpaper an original video, or an optimised one as a whole frame.
 * @param onFrame a new video frame waits in the texture: the preview draws it.
 */
@OptIn(UnstableApi::class)
internal class VideoPreviewSlide(
    context: Context,
    wallpaper: WallpaperSpec.Media,
    width: Int,
    height: Int,
    onFrame: () -> Unit,
) : PreviewSlide {

    private val clip = wallpaper.clip

    /** The clip's length where the spec knows it; an optimised video's comes from the player. */
    private val knownLoopMs = clip.endMsOrNull?.let { it - clip.startMs }
        ?: (wallpaper.mediaInfo.durationMs - clip.startMs).takeIf { wallpaper.mediaInfo.durationMs > 0 }

    private var released = false

    private val player: ExoPlayer = createLeanExoPlayer(context).apply {
        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.startMs)
            .setEndPositionMs(clip.endMsOrNull ?: C.TIME_END_OF_SOURCE)
            .build()
        setMediaItem(MediaItem.Builder().setUri(wallpaper.sourcePath.toUri()).setClippingConfiguration(clipping).build())
        prepare()
    }

    override val renderer = VideoWallpaperRenderer(
        renderParams = VideoRenderParams(width, height, wallpaper.crop, wallpaper.mediaInfo, wallpaper.tint),
        callbackHandler = Handler(Looper.getMainLooper()),
        listener = object : VideoWallpaperRenderer.Listener {
            override fun onFrameAvailable() = onFrame()

            // A new context gives a new texture: the player decodes into it from now on.
            override fun onVideoSurfaceCreated(surface: Surface) {
                if (!released) player.setVideoSurface(surface)
            }
        },
    )

    override fun moveTo(sinceShownMs: Long, playing: Boolean, seek: Boolean) {
        if (seek) {
            val loopMs = knownLoopMs ?: player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            player.seekTo(if (loopMs != null) sinceShownMs % loopMs else sinceShownMs)
        }
        player.playWhenReady = playing
    }

    override fun setScrubbing(scrubbing: Boolean) = player.setScrubbingModeEnabled(scrubbing)

    override fun release() {
        released = true
        player.release()
    }
}

/**
 * A picture slide: the wallpaper's own [PictureWallpaperRenderer], cropped, turned and tinted as on the home
 * screen, decoded on [decoder]. It has no time of its own.
 */
internal class PicturePreviewSlide(
    wallpaper: WallpaperSpec.Media,
    width: Int,
    height: Int,
    decoder: Executor,
    onDecoded: () -> Unit,
) : PreviewSlide {
    override val renderer = PictureWallpaperRenderer(
        path = wallpaper.sourcePath,
        frameWidth = wallpaper.mediaInfo.displayWidth,
        frameHeight = wallpaper.mediaInfo.displayHeight,
        crop = wallpaper.crop,
        screenWidth = width,
        screenHeight = height,
        onFailed = { error -> Timber.w(error, "The preview can't decode %s", wallpaper.sourcePath) },
        decodeOn = { decode ->
            decoder.execute {
                decode()
                onDecoded()
            }
        },
        tint = wallpaper.tint,
    )

    override fun moveTo(sinceShownMs: Long, playing: Boolean, seek: Boolean) = Unit

    override fun release() = Unit
}

/** A built-in slide: its [scene] at the slide's own time, which goes on every frame while it plays. */
internal class BuiltInPreviewSlide(private val scene: BuiltInScene, private val onMoved: () -> Unit) : PreviewSlide {
    override val renderer = scene.renderer

    override fun moveTo(sinceShownMs: Long, playing: Boolean, seek: Boolean) {
        scene.showAt(sinceShownMs * NANOS_PER_MS)
        onMoved()
    }

    override fun release() = scene.release()

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** A slide that can't be shown here: a built-in this device can't draw, say. It stays empty. */
internal object EmptyPreviewSlide : PreviewSlide {
    override val renderer: GLWallpaperRenderer? = null

    override fun moveTo(sinceShownMs: Long, playing: Boolean, seek: Boolean) = Unit

    override fun release() = Unit
}
