package com.onthecrow.wallper.service.engine

import android.content.Context
import android.view.SurfaceHolder
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.service.ExoPlayerFactory
import com.onthecrow.wallper.service.renderer.GLES20WallpaperRenderer
import com.onthecrow.wallper.service.renderer.GLWallpaperRenderer
import com.onthecrow.wallper.service.renderer.RendererParams
import com.onthecrow.wallper.util.MetadataUtils.getVideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GLWallpaperEngine(
    private val context: Context,
    private val exoPlayerFactory: ExoPlayerFactory,
    private val surfaceHolder: () -> SurfaceHolder,
    private val wallpaperEntity: WallpaperEntity,
) : WallpaperEngine() {

    private var glThread: WallpaperGLThread? = null
    private var mediaPlayer: ExoPlayer? = null
    private var renderer: GLWallpaperRenderer? = null

    init {
        Timber.d("created")

        val width = surfaceHolder().surfaceFrame.width()
        val height = surfaceHolder().surfaceFrame.height()

        // 2) медиаплеер
        mediaPlayer = exoPlayerFactory.create(
            context,
            wallpaperEntity.originalUri,
            wallpaperEntity.startPosition,
            wallpaperEntity.endPosition,
        )

        val videoMetadata = getVideoMetadata(context, wallpaperEntity.originalUri)
        // 1) создаём renderer (GLES20WallpaperRenderer)
        renderer = GLES20WallpaperRenderer(
            context = context,
            onFrameAvailable = {
                glThread?.requestRender()
            },
            renderParams = RendererParams.VideoParams(
                surfaceHolder().surfaceFrame.width(),
                surfaceHolder().surfaceFrame.height(),
                mediaPlayer!!,
                wallpaperEntity.shownRect,
                // TODO Bake video metadata in db on wallpaper creation (performance impact ~50ms)
                videoMetadata,
            ),
        )

        // 3) накидываем rendererParams (как у тебя в populateRenderer)
//        populateRenderer(wallpaperEntity)

        // 4) стартуем GL-поток
        glThread = WallpaperGLThread(surfaceHolder(), renderer!!).apply {
            start()
            onSurfaceCreatedOrChanged(width, height)
        }

        // 5) запускаем плеер
        mediaPlayer?.prepare()
        mediaPlayer?.play()
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

//    override fun setWallpaper(wallpaperEntity: WallpaperEntity) {
//        mediaPlayer = exoPlayerFactory.create(context, wallpaperEntity.originalUri/*, wallpaperEntity.startPosition, wallpaperEntity.endPosition*/)
//        populateRenderer(wallpaperEntity)
//    }

//    private fun populateRenderer(wallpaperEntity: WallpaperEntity) {
//        MainScope().launch(Dispatchers.IO) {
//            val videoMetadata = getVideoMetadata(context, wallpaperEntity.originalUri)
//            withContext(Dispatchers.Main) {
//                Timber.d(
//                    "Surface identity: ${
//                        System.identityHashCode(
//                            surfaceHolder().surface
//                        )
//                    }, mediaPlayer identity: ${System.identityHashCode(mediaPlayer)}"
//                )
//                if (mediaPlayer == null) return@withContext
//                renderer?.rendererParams = RendererParams.VideoParams(
//                    surfaceHolder().surfaceFrame.width(),
//                    surfaceHolder().surfaceFrame.height(),
//                    mediaPlayer!!,
//                    wallpaperEntity.shownRect,
//                    // TODO Bake video metadata in db on wallpaper creation (performance impact ~50ms)
//                    videoMetadata,
//                )
//                Timber.d("Renderer params set")
//            }
//        }
//    }
}