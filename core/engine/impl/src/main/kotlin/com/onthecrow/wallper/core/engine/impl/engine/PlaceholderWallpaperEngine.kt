package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.graphics.Bitmap
import android.view.SurfaceHolder
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toBitmap
import com.onthecrow.wallper.core.engine.impl.R
import com.onthecrow.wallper.core.gl.BitmapWallpaperRenderer
import com.onthecrow.wallper.core.gl.WallpaperGLThread
import timber.log.Timber

/**
 * GL-плейсхолдер, который рисует брендинг (bg_engine_empty.xml)
 * через WallpaperGLThread / OpenGL.
 */
internal class PlaceholderWallpaperEngine(
    context: Context,
    surfaceHolder: () -> SurfaceHolder,
) : WallpaperEngine() {

    private var glThread: WallpaperGLThread? = null
    private var renderer: BitmapWallpaperRenderer? = null

    init {
        Timber.d("PlaceholderWallpaperEngine created")

        val width = surfaceHolder().surfaceFrame.width()
        val height = surfaceHolder().surfaceFrame.height()

        val brandingRenderer = BitmapWallpaperRenderer(width, height) { bitmapWidth, bitmapHeight ->
            val drawable = AppCompatResources.getDrawable(context, R.drawable.bg_engine_empty)
                ?: throw IllegalStateException("bg_engine_empty drawable not found")
            drawable.toBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        }
        renderer = brandingRenderer

        // continuous = false → можем потом переключиться на when-dirty, но для плейсхолдера не критично
        glThread = WallpaperGLThread(surfaceHolder(), brandingRenderer, continuous = false).apply {
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
}
