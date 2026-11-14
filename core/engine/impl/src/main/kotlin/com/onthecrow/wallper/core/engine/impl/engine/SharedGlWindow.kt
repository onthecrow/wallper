package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.gl.SharedGl

/** The GL thread a series' slides share, as the stage's [SeriesWindow]. */
internal class SharedGlWindow(private val gl: SharedGl) : SeriesWindow {

    override fun setVisible(visible: Boolean) = gl.setVisible(visible)

    override fun onSurfaceChanged(width: Int, height: Int) = gl.onSurfaceChanged(width, height)

    // The system frees the window right after this returns: wait for the EGL surface to go.
    override fun onSurfaceDestroyed() = gl.onSurfaceDestroyed(SURFACE_DESTROY_WAIT_MS)

    override fun release() = gl.release(STOP_TIMEOUT_MS)

    private companion object {
        const val STOP_TIMEOUT_MS = 1_500L
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
    }
}
