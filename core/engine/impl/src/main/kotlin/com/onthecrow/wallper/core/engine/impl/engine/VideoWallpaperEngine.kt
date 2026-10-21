package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.core.player.ExoPlayerFactory
import timber.log.Timber

/** Plays a video the editor has already cropped and trimmed, straight onto the wallpaper surface. */
@UnstableApi
internal class VideoWallpaperEngine(
    context: Context,
    exoPlayerFactory: ExoPlayerFactory,
    surfaceHolder: () -> SurfaceHolder,
    processedPath: String,
) : WallpaperEngine() {

    private var mediaPlayer: ExoPlayer? = null

    init {
        Timber.d("created")
        mediaPlayer = exoPlayerFactory.create(context, processedPath).apply {
            setVideoSurfaceHolder(surfaceHolder())
            prepare()
            play()
        }
    }

    override fun pause() {
        Timber.d("paused")
        mediaPlayer?.pause()
    }

    override fun resume() {
        Timber.d("resumed")
        mediaPlayer?.play()
    }

    override fun release() {
        Timber.d("release")
        mediaPlayer?.stop()
        mediaPlayer?.release()
    }
}
