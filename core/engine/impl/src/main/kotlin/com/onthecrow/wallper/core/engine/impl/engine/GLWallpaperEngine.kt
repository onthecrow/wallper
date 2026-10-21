package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.gl.GLWallpaperRenderer
import com.onthecrow.wallper.core.gl.VideoRenderParams
import com.onthecrow.wallper.core.gl.VideoWallpaperRenderer
import com.onthecrow.wallper.core.gl.WallpaperGLThread
import com.onthecrow.wallper.core.player.ExoPlayerFactory
import timber.log.Timber

/** Plays the original video and crops it on the GPU through UV coordinates. */
@UnstableApi
internal class GLWallpaperEngine(
    context: Context,
    exoPlayerFactory: ExoPlayerFactory,
    surfaceHolder: () -> SurfaceHolder,
    wallpaper: WallpaperSpec,
) : WallpaperEngine() {

    private var glThread: WallpaperGLThread? = null
    private var mediaPlayer: ExoPlayer? = null
    private var renderer: GLWallpaperRenderer? = null

    init {
        Timber.d("created")

        val width = surfaceHolder().surfaceFrame.width()
        val height = surfaceHolder().surfaceFrame.height()

        // 1) медиаплеер
        val player = exoPlayerFactory.create(
            context,
            wallpaper.sourcePath,
            wallpaper.clip.startMs,
            wallpaper.clip.endMs,
        )
        mediaPlayer = player

        // 2) рендерер; размеры и поворот видео сохранены в БД при создании обоев
        val videoRenderer = VideoWallpaperRenderer(
            renderParams = VideoRenderParams(width, height, wallpaper.crop, wallpaper.mediaInfo),
            onFrameAvailable = { glThread?.requestRender() },
            onVideoSurfaceCreated = { surface -> mediaPlayer?.setVideoSurface(surface) },
        )
        renderer = videoRenderer

        // 3) стартуем GL-поток
        glThread = WallpaperGLThread(surfaceHolder(), videoRenderer).apply {
            start()
            onSurfaceCreatedOrChanged(width, height)
        }

        // 4) запускаем плеер
        player.prepare()
        player.play()
    }

    override fun pause() {
        Timber.d("paused")
        mediaPlayer?.pause()
        glThread?.onPauseRendering()
    }

    override fun resume() {
        Timber.d("resumed")
        glThread?.onResumeRendering()
        mediaPlayer?.play()
    }

    override fun release() {
        Timber.d("released")
        mediaPlayer?.stop()
        mediaPlayer?.clearVideoSurface()
        mediaPlayer?.release()
        mediaPlayer = null

        // 2. Остановить GL-поток
        glThread?.requestExitAndWait()
        glThread = null

        // 3. Освободить OpenGLScene/текстуры/SurfaceTexture/Surface
        renderer?.dispose()
        renderer = null
    }
}
