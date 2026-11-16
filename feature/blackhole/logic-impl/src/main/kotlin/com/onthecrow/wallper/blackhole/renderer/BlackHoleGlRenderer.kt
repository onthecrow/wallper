package com.onthecrow.wallper.blackhole.renderer

import android.opengl.GLES20
import android.os.SystemClock
import com.onthecrow.wallper.blackhole.physics.FrameState
import com.onthecrow.wallper.core.gl.GLWallpaperRenderer
import java.util.concurrent.atomic.AtomicReference

/**
 * [BlackHoleRenderer] on [com.onthecrow.wallper.core.gl.WallpaperGLThread]. The owner computes frames
 * elsewhere and hands over the newest one through [submit]; each draw renders the latest.
 * [onFailure] hears once, on the GL thread, that the shaders don't build on this device.
 */
internal class BlackHoleGlRenderer(
    val renderer: BlackHoleRenderer,
    private val onFailure: (Throwable) -> Unit = {},
) : GLWallpaperRenderer() {

    private val latest = AtomicReference<FrameState?>(null)
    private val boundFramebuffer = IntArray(1)
    private var failureReported = false

    /** The last frame drew the black hole: without its shaders or lens tables a frame is black. */
    override var hasPicture = false
        private set

    /** Any thread. Request a render afterwards. */
    fun submit(frame: FrameState) = latest.set(frame)

    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(width: Int, height: Int) = renderer.onSurfaceSize(width, height)

    /**
     * Into whatever framebuffer is bound: the window, or a series' target while it blends two slides. Read
     * first, as setting up binds others.
     */
    override fun onDrawFrame(): Boolean {
        val frame = latest.get() ?: return false
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, boundFramebuffer, 0)
        val drawn = renderer.render(frame, SystemClock.elapsedRealtimeNanos(), outputFramebuffer = boundFramebuffer[0])
        hasPicture = drawn
        val failure = renderer.failure
        if (failure != null && !failureReported) {
            failureReported = true
            onFailure(failure)
        }
        return drawn
    }


    override fun onContextLost() {
        hasPicture = false
        renderer.onContextLost()
    }

    override fun dispose() = renderer.release()
}
