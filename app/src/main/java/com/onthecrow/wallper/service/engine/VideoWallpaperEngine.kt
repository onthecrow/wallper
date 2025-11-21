package com.onthecrow.wallper.service.engine

import android.content.Context
import android.view.SurfaceHolder
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.service.ExoPlayerFactory
import timber.log.Timber

class VideoWallpaperEngine(
    private val context: Context,
    private val exoPlayerFactory: ExoPlayerFactory,
    private val surfaceHolder: () -> SurfaceHolder,
    private val wallpaperEntity: WallpaperEntity,
) : WallpaperEngine() {

    private var mediaPlayer: ExoPlayer? = null

    init {
        Timber.d("created")
        mediaPlayer = exoPlayerFactory.create(context, wallpaperEntity.processedUri).apply {
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
