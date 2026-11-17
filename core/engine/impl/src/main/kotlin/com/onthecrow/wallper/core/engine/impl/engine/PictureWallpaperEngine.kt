package com.onthecrow.wallper.core.engine.impl.engine

import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.gl.PictureWallpaperRenderer
import com.onthecrow.wallper.core.gl.WallpaperGLThread
import timber.log.Timber

/**
 * The user's picture, cropped and turned like an original video and panned with the launcher's pages
 * ([PictureWallpaperRenderer]); with the user's setting off the service reports no pages. A still frame on
 * a [WallpaperGLThread] of its own, like the placeholder's: drawn when the offset or the surface changes,
 * never on a timer, so it needs no heartbeat. A picture that can't be decoded shows the placeholder.
 */
internal class PictureWallpaperEngine(
    surfaceHolder: () -> SurfaceHolder,
    wallpaper: WallpaperSpec.Media,
    private val host: EngineHost,
) : WallpaperEngine() {

    private val holder = surfaceHolder()

    @Volatile
    private var released = false

    private val renderer = PictureWallpaperRenderer(
        path = wallpaper.sourcePath,
        frameWidth = wallpaper.mediaInfo.displayWidth,
        frameHeight = wallpaper.mediaInfo.displayHeight,
        crop = wallpaper.crop,
        screenWidth = holder.surfaceFrame.width(),
        screenHeight = holder.surfaceFrame.height(),
        // On the GL thread: the host posts to the main thread, which then releases this engine.
        onFailed = { error -> if (!released) host.showPlaceholder("The picture can't be decoded", error) },
    )

    private val glThread = WallpaperGLThread(holder, renderer, continuous = false).apply {
        start()
        onSurfaceCreatedOrChanged(holder.surfaceFrame.width(), holder.surfaceFrame.height())
        requestRender()
    }

    init {
        Timber.d("created")
    }

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        renderer.pageOffset = xOffset.takeIf { xOffsetStep > 0f }
        glThread.requestRender()
    }

    override fun onSurfaceChanged(width: Int, height: Int) = glThread.onSurfaceCreatedOrChanged(width, height)

    // The system may free the window right after this returns: wait for the EGL surface to go.
    override fun onSurfaceDestroyed() = glThread.onSurfaceDestroyed(waitMs = SURFACE_DESTROY_WAIT_MS)

    override fun pause() {
        Timber.d("paused")
        glThread.onPauseRendering()
    }

    override fun resume() {
        Timber.d("resumed")
        glThread.onResumeRendering()
        glThread.requestRender()
    }

    override fun release() {
        Timber.d("released")
        released = true
        glThread.requestExitAndWait()
    }

    private companion object {
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
