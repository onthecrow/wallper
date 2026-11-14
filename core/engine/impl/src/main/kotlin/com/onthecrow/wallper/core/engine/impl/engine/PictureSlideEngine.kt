package com.onthecrow.wallper.core.engine.impl.engine

import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.SlideStart
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.gl.GlClient
import com.onthecrow.wallper.core.gl.GlLink
import com.onthecrow.wallper.core.gl.PictureWallpaperRenderer
import timber.log.Timber

/**
 * The user's picture as a slide of a series that prepares its slides ahead: [PictureWallpaperEngine]'s
 * picture, on the GL host the series gives it ([slide]). It is decoded on this engine's own thread, since
 * the GL thread may be drawing the slide on screen, and is ready once it is a texture and drawn. A still
 * frame: drawn when the offset or the surface changes, never on a timer.
 */
internal class PictureSlideEngine(
    surfaceHolder: () -> SurfaceHolder,
    private val wallpaper: WallpaperSpec.Media,
    private val host: EngineHost,
    private val slide: SlideStart,
) : WallpaperEngine() {

    private val holder = surfaceHolder()

    /** Decoding and what the GL side tells this engine, off the main thread. */
    private val callbackThread = HandlerThread("PictureSlide").apply { start() }
    private val handler = Handler(callbackThread.looper)

    @Volatile
    private var released = false

    /** What the renderer pans with; kept here for a renderer made after a GL restart. */
    @Volatile
    private var pageOffset: Float? = null

    private val gl: GlLink<PictureWallpaperRenderer> = slide.gl.open(handler, Renderers())

    init {
        Timber.d("created")
        gl.start()
        gl.requestRender()
    }

    override fun pause() = gl.setVisible(false)

    override fun resume() {
        gl.setVisible(true)
        gl.requestRender()
    }

    override fun show() = gl.show()

    override fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {
        pageOffset = xOffset.takeIf { xOffsetStep > 0f }
        gl.renderer.pageOffset = pageOffset
        gl.requestRender()
    }

    override fun onSurfaceChanged(width: Int, height: Int) = gl.onSurfaceChanged(width, height)

    // The next slide, or the system, takes the window right after this returns.
    override fun onSurfaceDestroyed() = gl.onSurfaceDestroyed(SURFACE_DESTROY_WAIT_MS)

    override fun release() {
        Timber.d("released")
        released = true
        gl.release(STOP_TIMEOUT_MS)
        callbackThread.quitSafely()
    }

    private inner class Renderers : GlClient<PictureWallpaperRenderer> {

        override fun newRenderer() = PictureWallpaperRenderer(
            path = wallpaper.sourcePath,
            frameWidth = wallpaper.mediaInfo.displayWidth,
            frameHeight = wallpaper.mediaInfo.displayHeight,
            crop = wallpaper.crop,
            screenWidth = holder.surfaceFrame.width(),
            screenHeight = holder.surfaceFrame.height(),
            // On the GL thread: the host posts to the main thread, which then releases this engine.
            onFailed = { error -> if (!released) host.showPlaceholder("The picture can't be decoded", error) },
            decodeOn = { decode ->
                handler.post {
                    decode()
                    if (!released) gl.requestRender()
                }
            },
        ).apply { pageOffset = this@PictureSlideEngine.pageOffset }

        override fun onFirstPicture() = slide.onReady()

        override fun onGiveUp(reason: String, error: Throwable?) = host.showPlaceholder(reason, error)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 1_500L
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
