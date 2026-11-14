package com.onthecrow.wallper.core.gl

import android.os.Handler

/**
 * Where a wallpaper's renderer runs: on a GL thread of its own ([OwnGl]), or on one it shares with the
 * other slides of a series ([SharedGl]). An engine asks for a [GlLink] and works the same on either.
 */
interface GlHost {
    /**
     * @param handler the engine's callback thread: [client] hears from GL there, unless said otherwise.
     * @return the link, not started yet.
     */
    fun <R : GLWallpaperRenderer> open(handler: Handler, client: GlClient<R>): GlLink<R>
}

/** What a [GlHost] asks of the engine whose renderer it runs. */
interface GlClient<R : GLWallpaperRenderer> {
    /** 2 or 3: the context the renderer needs. */
    val glesVersion: Int get() = 2

    /**
     * A renderer for a GL thread that hasn't drawn yet: the first one inside [GlLink.start], on the thread
     * that calls it, and one more on the handler after each GL restart.
     */
    fun newRenderer(): R

    /** Before a broken GL thread is stopped: e.g. take the video decoder off its texture. Any thread. */
    fun beforeRestart() {}

    /** On the handler, once [newRenderer]'s renderer runs on a new GL thread. */
    fun afterRestart() {}

    /** On the handler: the renderer has drawn its picture for the first time, off the window if it isn't shown yet. */
    fun onFirstPicture() {}

    /** GL can't draw this wallpaper, here or any more. Any thread. */
    fun onGiveUp(reason: String, error: Throwable?)
}

/** An engine's hold on the GL thread that runs its renderer. Thread-safe unless said otherwise. */
interface GlLink<R : GLWallpaperRenderer> {
    /** The renderer running now; another one after a GL restart. Only valid after [start]. */
    val renderer: R

    fun start()

    /** Whether [renderer] is the one running now, not one of a GL thread that was replaced. */
    fun isCurrent(renderer: R): Boolean

    fun requestRender()

    /** Runs [task] on the GL thread with the renderer running then. */
    fun post(task: (R) -> Unit)

    fun setVisible(visible: Boolean)

    /** Frames are expected now (visible, and something animates): a GL thread that stops drawing is noticed. */
    fun setFramesExpected(expected: Boolean)

    /** The next frames compile shaders and may take seconds. */
    fun expectSlowFrame()

    /** [renderer] can never draw here: the client hears [GlClient.onGiveUp], if it is still the current one. */
    fun fail(renderer: R, error: Throwable)

    /**
     * For a link opened off the window: from now on its renderer draws there, and its first picture is
     * shown even while hidden. Whoever had the window has let go of it.
     */
    fun show()

    fun onSurfaceChanged(width: Int, height: Int)

    /** Blocks up to [waitMs] until the window is let go of: the system, or the next slide, takes it right after. */
    fun onSurfaceDestroyed(waitMs: Long)

    /** Stops drawing and frees the renderer; waits up to [timeoutMs] where a thread has to stop. */
    fun release(timeoutMs: Long)
}
