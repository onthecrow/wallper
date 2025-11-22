package com.onthecrow.wallper.service.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.service.renderer.BrandingWallpaperRenderer
import timber.log.Timber

/**
 * GL-плейсхолдер, который рисует брендинг (bg_engine_empty.xml)
 * через WallpaperGLThread / OpenGL.
 */
class PlaceholderWallpaperEngine(
    context: Context,
    surfaceHolder: () -> SurfaceHolder,
) : WallpaperEngine() {

    private var glThread: WallpaperGLThread? = null
    private var renderer: BrandingWallpaperRenderer? = null

    init {
        Timber.d("PlaceholderWallpaperEngine created")

        val width = surfaceHolder().surfaceFrame.width()
        val height = surfaceHolder().surfaceFrame.height()

        renderer = BrandingWallpaperRenderer(context, width, height)

        // continuous = false → можем потом переключиться на when-dirty, но для плейсхолдера не критично
        glThread = WallpaperGLThread(surfaceHolder(), renderer!!, continuous = false).apply {
            start()
            onSurfaceCreatedOrChanged(width, height)
            // один раз попросим кадр
            requestRender()
        }
    }

    override fun pause() {
        Timber.d("PlaceholderWallpaperEngine paused")
        glThread?.onPauseRendering()
    }

    override fun resume() {
        Timber.d("PlaceholderWallpaperEngine resumed")
        glThread?.onResumeRendering()
        glThread?.requestRender()
    }

    override fun release() {
        Timber.d("PlaceholderWallpaperEngine released")

        glThread?.requestExitAndWait()
        glThread = null

        renderer?.dispose()
        renderer = null
    }

    /**
     * Если у тебя в базовом интерфейсе/абстрактном классе есть колбэк
     * onSurfaceSizeChanged(...) – можно прокинуть его сюда:
     */
//    fun onSurfaceSizeChanged(width: Int, height: Int) {
//        glThread?.onSurfaceCreatedOrChanged(width, height)
//        glThread?.requestRender()
//    }
}
