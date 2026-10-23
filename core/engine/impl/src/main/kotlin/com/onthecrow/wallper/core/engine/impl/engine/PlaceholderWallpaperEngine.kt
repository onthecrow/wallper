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
 * The placeholder: draws the branding (bg_engine_empty.xml) once on a [WallpaperGLThread].
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

        // When dirty: one still frame, redrawn only when the surface changes.
        glThread = WallpaperGLThread(surfaceHolder(), brandingRenderer, continuous = false).apply {
            start()
            onSurfaceCreatedOrChanged(width, height)
            requestRender()
        }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        glThread?.onSurfaceCreatedOrChanged(width, height)
    }

    // The system may free the window right after this returns: wait for the EGL surface to go.
    override fun onSurfaceDestroyed() {
        glThread?.onSurfaceDestroyed(waitMs = SURFACE_DESTROY_WAIT_MS)
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

    private companion object {
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
