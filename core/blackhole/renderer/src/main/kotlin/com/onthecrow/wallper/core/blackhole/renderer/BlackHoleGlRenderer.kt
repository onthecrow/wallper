package com.onthecrow.wallper.core.blackhole.renderer

import android.os.SystemClock
import com.onthecrow.wallper.core.blackhole.physics.FrameState
import com.onthecrow.wallper.core.gl.GLWallpaperRenderer
import java.util.concurrent.atomic.AtomicReference

/**
 * [BlackHoleRenderer] on [com.onthecrow.wallper.core.gl.WallpaperGLThread]. The owner computes frames
 * elsewhere and hands over the newest one through [submit]; each draw renders the latest.
 * [onFailure] hears once, on the GL thread, that the shaders don't build on this device.
 */
class BlackHoleGlRenderer(
    val renderer: BlackHoleRenderer,
    private val onFailure: (Throwable) -> Unit = {},
) : GLWallpaperRenderer() {

    private val latest = AtomicReference<FrameState?>(null)
    private var failureReported = false

    /** Any thread. Request a render afterwards. */
    fun submit(frame: FrameState) = latest.set(frame)

    override fun onSurfaceCreated() = Unit

    override fun onSurfaceChanged(width: Int, height: Int) = renderer.onSurfaceSize(width, height)

    override fun onDrawFrame(): Boolean {
        val frame = latest.get() ?: return false
        val drawn = renderer.render(frame, SystemClock.elapsedRealtimeNanos())
        val failure = renderer.failure
        if (failure != null && !failureReported) {
            failureReported = true
            onFailure(failure)
        }
        return drawn
    }

    override fun setOffset(xOffset: Float, yOffset: Float) = Unit

    override fun onContextLost() = renderer.onContextLost()

    override fun dispose() = renderer.release()
}
