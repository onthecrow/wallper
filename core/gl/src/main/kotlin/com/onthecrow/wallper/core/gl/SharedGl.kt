package com.onthecrow.wallper.core.gl

import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceHolder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * One GL thread, one context and one surface on the window for all the slides of a series. Each slide's
 * engine opens a link and has its own renderer run here ([SlidesRenderer]): drawn off screen while the
 * slide warms up, on screen from [GlLink.show] on. A change of slide is then just the next frame, with no
 * hand-over of the window. The thread shows no frame without a picture, so the window keeps its last one
 * while a slide that wasn't prepared gets ready.
 *
 * The thread is the series', not a slide's: the series tells it about visibility and the surface
 * ([setVisible] and the others), and what a link says of those is ignored. When GL breaks it is restarted
 * as for any wallpaper ([RestartableGl]) and every slide makes its renderer anew; when that doesn't help,
 * the series as a whole hears [onGiveUp].
 *
 * @param glesVersion the highest version a slide's renderer asks for.
 */
class SharedGl(
    private val holder: SurfaceHolder,
    private val glesVersion: Int,
    onGiveUp: (reason: String, error: Throwable?) -> Unit,
) : GlHost {

    /** The heartbeat and the restarts of the thread. */
    private val callbackThread = HandlerThread("SeriesGl").apply { start() }
    private val links = CopyOnWriteArrayList<Link<*>>()

    /** The link of the slide on screen: the thread expects frames when that slide does. */
    @Volatile
    private var shown: Link<*>? = null

    private inner class Session : RestartableGl.Session {
        val slides = SlidesRenderer(GlState::reset)
        override val thread = WallpaperGLThread(
            surfaceHolder = holder,
            renderer = slides,
            renderGuardListener = { stall -> gl.restart(this, "unrecoverable $stall") },
            glesVersion = glesVersion,
            onInitFailed = { error -> gl.fail(this, error) },
            gated = true,
        )
    }

    private val gl: RestartableGl<Session> = RestartableGl(
        holder = holder,
        handler = Handler(callbackThread.looper),
        onGiveUp = onGiveUp,
        beforeRestart = { links.forEach { it.beforeRestart() } },
        afterRestart = { links.forEach { it.afterRestart() } },
        newSession = { Session() },
    )

    init {
        gl.start()
    }

    override fun <R : GLWallpaperRenderer> open(handler: Handler, client: GlClient<R>): GlLink<R> = Link(handler, client)

    fun setVisible(visible: Boolean) {
        gl.setVisible(visible)
        // A slide that changed while hidden asked for frames nobody drew.
        if (visible) gl.current.thread.requestRender()
    }

    fun onSurfaceChanged(width: Int, height: Int) = gl.onSurfaceChanged(width, height)

    /** Blocks up to [waitMs] until the window is let go of. */
    fun onSurfaceDestroyed(waitMs: Long) = gl.onSurfaceDestroyed(waitMs)

    /** Stops the thread, which disposes of every renderer still here, and waits up to [timeoutMs] for it. */
    fun release(timeoutMs: Long) {
        gl.release(timeoutMs)
        callbackThread.quitSafely()
    }

    private enum class Role { WARMING, SHOWN, RELEASED }

    private inner class Link<R : GLWallpaperRenderer>(
        private val clientHandler: Handler,
        private val client: GlClient<R>,
    ) : GlLink<R> {

        /** A renderer and its place on the GL thread that runs it; a restart of GL makes another. */
        private inner class Running(val renderer: R, val session: Session) {
            val slide = SlidesRenderer.Slide(
                renderer = renderer,
                onFirstPicture = { clientHandler.post { if (running === this) client.onFirstPicture() } },
                onFailed = { error -> client.onGiveUp("The slide's renderer failed", error) },
            )
        }

        @Volatile
        private var running: Running? = null

        @Volatile
        private var role = Role.WARMING

        @Volatile
        private var framesExpected = false

        override val renderer: R get() = checkNotNull(running) { "The link isn't started" }.renderer

        override fun start() {
            links += this
            run(client.newRenderer())
        }

        override fun isCurrent(renderer: R): Boolean = running?.renderer === renderer

        override fun requestRender() {
            val now = running ?: return
            when (role) {
                Role.SHOWN -> now.session.thread.requestRender()
                Role.WARMING -> warm(now)
                Role.RELEASED -> Unit
            }
        }

        override fun post(task: (R) -> Unit) {
            val now = running ?: return
            now.session.thread.post { task(now.renderer) }
        }

        // The series shows and hides the thread, and tells it about the surface.
        override fun setVisible(visible: Boolean) = Unit

        override fun onSurfaceChanged(width: Int, height: Int) = Unit

        override fun onSurfaceDestroyed(waitMs: Long) = Unit

        override fun setFramesExpected(expected: Boolean) {
            framesExpected = expected
            if (shown === this) gl.setFramesExpected(expected)
        }

        override fun expectSlowFrame() = gl.expectSlowFrame()

        override fun fail(renderer: R, error: Throwable) {
            if (isCurrent(renderer)) client.onGiveUp("GL can't render this slide here", error)
        }

        override fun show() {
            role = Role.SHOWN
            shown = this
            gl.setFramesExpected(framesExpected)
            running?.let(::present)
        }

        /** The renderer is disposed of on the GL thread in its own time: nothing here waits for it. */
        override fun release(timeoutMs: Long) {
            role = Role.RELEASED
            links -= this
            if (shown === this) {
                shown = null
                gl.setFramesExpected(false)
            }
            val last = running ?: return
            running = null
            last.session.thread.post { last.session.slides.remove(last.slide) }
        }

        fun beforeRestart() = client.beforeRestart()

        /** A new GL thread starts with no slides: this one's renderer is made anew, on its engine's thread. */
        fun afterRestart() {
            clientHandler.post {
                if (role == Role.RELEASED) return@post
                run(client.newRenderer())
                client.afterRestart()
            }
        }

        /** Puts [renderer] on the GL thread there is now, in the place this link has: on screen or warming up. */
        private fun run(renderer: R) {
            val next = Running(renderer, gl.current)
            running = next
            when (role) {
                Role.SHOWN -> present(next)
                Role.WARMING -> warm(next)
                Role.RELEASED -> Unit
            }
        }

        private fun warm(target: Running) {
            val session = target.session
            session.thread.post {
                // A slide's first frames build its shaders and textures: seconds on a cold driver cache.
                gl.expectSlowFrame()
                if (!session.slides.warm(target.slide)) session.thread.requestRender()
            }
        }

        /** On screen from the next frame, which is shown even while the wallpaper is hidden. */
        private fun present(target: Running) {
            val session = target.session
            session.thread.post {
                gl.expectSlowFrame()
                session.slides.show(target.slide)
            }
            session.thread.presentOnce()
            session.thread.requestRender()
        }
    }
}
