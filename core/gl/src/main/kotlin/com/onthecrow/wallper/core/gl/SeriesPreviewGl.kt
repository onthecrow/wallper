package com.onthecrow.wallper.core.gl

import android.view.SurfaceHolder
import com.onthecrow.wallper.core.model.TransitionEffect
import timber.log.Timber

/**
 * One GL thread on a screen's surface for a series' preview: its [PreviewCompositor] draws the frame it is
 * told, through [effect] where it blends two slides. Unlike the wallpaper's threads nothing restarts it: the
 * preview is seconds of a screen, not days of a home screen. A frame without a picture isn't shown, so the
 * surface keeps the last one while a slide gets ready.
 *
 * @param glesVersion the highest version a slide's renderer needs.
 */
class SeriesPreviewGl(
    holder: SurfaceHolder,
    glesVersion: Int,
    effect: TransitionEffect?,
) {
    private val compositor = PreviewCompositor(
        canvas = effect?.takeUnless { it == TransitionEffect.NONE }?.let(::GlTransitionCanvas),
        resetState = GlState::reset,
        width = holder.surfaceFrame.width(),
        height = holder.surfaceFrame.height(),
    )

    private val thread = WallpaperGLThread(
        surfaceHolder = holder,
        renderer = compositor,
        renderGuardListener = { stall -> Timber.w("The preview's GL can't be repaired: %s", stall) },
        glesVersion = glesVersion,
        gated = true,
    ).apply {
        onSurfaceCreatedOrChanged(holder.surfaceFrame.width(), holder.surfaceFrame.height())
        start()
    }

    /** Sets [renderer] up on the GL thread, ahead of its frames. */
    fun add(renderer: GLWallpaperRenderer) = thread.post { compositor.add(renderer) }

    /** Disposes of [renderer] on the GL thread; a frame drawing it must have been replaced before. */
    fun remove(renderer: GLWallpaperRenderer) = thread.post { compositor.remove(renderer) }

    /** Draws [frame] from the next frame on. */
    fun show(frame: PreviewFrame?) {
        compositor.frame = frame
        thread.requestRender()
    }

    /** Something in the frame changed: a video frame came, a picture was decoded. */
    fun requestRender() = thread.requestRender()

    fun setVisible(visible: Boolean) = if (visible) thread.onResumeRendering() else thread.onPauseRendering()

    fun onSurfaceChanged(width: Int, height: Int) = thread.onSurfaceCreatedOrChanged(width, height)

    /** Stops the thread, which disposes of every renderer still there; with [surfaceGoes] it first lets go of the surface. */
    fun release(surfaceGoes: Boolean) {
        if (surfaceGoes) thread.onSurfaceDestroyed(SURFACE_DESTROY_WAIT_MS)
        thread.requestExitAndWait(STOP_TIMEOUT_MS)
    }

    private companion object {
        const val SURFACE_DESTROY_WAIT_MS = 1_000L
        const val STOP_TIMEOUT_MS = 1_500L
    }
}
