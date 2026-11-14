package com.onthecrow.wallper.core.gl

import android.os.Handler
import android.view.SurfaceHolder

/**
 * A GL thread of its own for each renderer, replaced as a whole when it breaks or hangs ([RestartableGl]):
 * what every wallpaper outside a series has.
 *
 * @param offWindow the thread starts without the window and prepares on a pbuffer until [GlLink.show]:
 * a series' next slide, made while the one before it still has the window. Such a thread shows no frame
 * before its renderer has its picture.
 */
class OwnGl(
    private val holder: SurfaceHolder,
    private val offWindow: Boolean = false,
) : GlHost {

    override fun <R : GLWallpaperRenderer> open(handler: Handler, client: GlClient<R>): GlLink<R> =
        Link(handler, client)

    private inner class Link<R : GLWallpaperRenderer>(
        private val handler: Handler,
        private val client: GlClient<R>,
    ) : GlLink<R> {

        /** One GL thread with its own renderer: a restart never shares GL state with an abandoned thread. */
        private inner class Session : RestartableGl.Session {
            val renderer: R = client.newRenderer()
            override val thread = WallpaperGLThread(
                surfaceHolder = holder,
                renderer = renderer,
                renderGuardListener = { stall -> gl.restart(this, "unrecoverable $stall") },
                glesVersion = client.glesVersion,
                // ES 2 is always there; only a renderer that asks for more can be refused a context.
                onInitFailed = if (client.glesVersion > 2) { error -> gl.fail(this, error) } else null,
                gated = offWindow,
                onFirstPicture = if (offWindow) { -> handler.post { if (gl.isCurrent(this)) client.onFirstPicture() } } else null,
            )
        }

        private val gl: RestartableGl<Session> = RestartableGl(
            holder = holder,
            handler = handler,
            onGiveUp = client::onGiveUp,
            beforeRestart = client::beforeRestart,
            afterRestart = client::afterRestart,
            onWindow = !offWindow,
            newSession = { Session() },
        )

        override val renderer: R get() = gl.current.renderer

        override fun start() = gl.start()

        override fun isCurrent(renderer: R): Boolean = gl.current.renderer === renderer

        override fun requestRender() = gl.current.thread.requestRender()

        override fun post(task: (R) -> Unit) {
            val session = gl.current
            session.thread.post { task(session.renderer) }
        }

        override fun setVisible(visible: Boolean) = gl.setVisible(visible)

        override fun setFramesExpected(expected: Boolean) = gl.setFramesExpected(expected)

        override fun expectSlowFrame() = gl.expectSlowFrame()

        override fun fail(renderer: R, error: Throwable) {
            val session = gl.current.takeIf { it.renderer === renderer } ?: return
            gl.fail(session, error)
        }

        override fun show() = gl.attach()

        override fun onSurfaceChanged(width: Int, height: Int) = gl.onSurfaceChanged(width, height)

        override fun onSurfaceDestroyed(waitMs: Long) = gl.onSurfaceDestroyed(waitMs)

        override fun release(timeoutMs: Long) = gl.release(timeoutMs)
    }
}
