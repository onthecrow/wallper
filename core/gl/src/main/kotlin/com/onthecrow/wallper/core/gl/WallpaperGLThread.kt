package com.onthecrow.wallper.core.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.SystemClock
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.diagnostics.Diagnostics
import com.onthecrow.wallper.core.gl.guard.RenderGuard
import com.onthecrow.wallper.core.gl.guard.RenderStall
import com.onthecrow.wallper.core.gl.guard.RenderRepairs
import timber.log.Timber

/**
 * A GL thread of its own that draws straight into the SurfaceHolder of a WallpaperService.Engine and
 * drives a [GLWallpaperRenderer].
 *
 * With a [renderGuardListener] the thread owns a [RenderGuard]: swap failures and exceptions are repaired
 * here (new EGL surface or context), and what can't be repaired is reported to the listener.
 * [lastLoopAtMs] (in [nowMs] time, elapsedRealtime by default) lets the owner notice a thread that hangs
 * inside a driver call, which no in-thread check can.
 *
 * A thread may start before it has the window ([prepareOffWindow]): it then renders into a pbuffer, so
 * that everything is made and the first picture drawn by the time the window comes; the same context
 * moves onto it.
 *
 * @param glesVersion 2 or 3. If the device can't create such a context, [onInitFailed] is called and the
 * thread ends.
 * @param gated a frame is shown only once the renderer has its picture ([GLWallpaperRenderer.hasPicture]):
 * until then the window keeps whatever was there, the wallpaper before this one. A gated thread that
 * can't get the window while no frames are expected tells [renderGuardListener] at once: nobody else would
 * notice.
 * @param onFirstPicture called once, on this thread, after the first frame with the picture: shown, or
 * drawn off the window.
 */
class WallpaperGLThread(
    private val surfaceHolder: SurfaceHolder,
    private val renderer: GLWallpaperRenderer,
    private val continuous: Boolean = false,
    private val renderGuardListener: RenderGuard.Listener? = null,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    private val glesVersion: Int = 2,
    private val onInitFailed: ((Throwable) -> Unit)? = null,
    private val gated: Boolean = false,
    private val onFirstPicture: (() -> Unit)? = null,
) : Thread("WallpaperGLThread") {

    @Volatile
    private var running = true

    @Volatile
    private var paused = false

    @Volatile
    private var hasSurface = false

    @Volatile
    private var surfaceWidth = 0

    @Volatile
    private var surfaceHeight = 0

    @Volatile
    private var renderRequested = false

    /** Before the window: frames go to a pbuffer and none is shown. */
    @Volatile
    private var offWindow = false

    private val lock = Object()

    // Guarded by lock
    private val tasks = ArrayDeque<() -> Unit>()
    private var framesExpected = false

    /** The next frame is shown even while paused. */
    private var presentRequested = false

    /** When the render loop last ran. Stops moving if the thread hangs or dies. */
    @Volatile
    var lastLoopAtMs: Long = nowMs()
        private set

    private val renderGuard = renderGuardListener?.let { listener ->
        RenderGuard(Repairs(), listener, nowMs) { Timber.tag(GUARD_TAG).d(it) }
    }

    // EGL objects
    private var eglDisplay: EGLDisplay? = null
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // When to call onSurfaceCreated / onSurfaceChanged
    private var surfaceCreatedDelivered = false
    private var pendingSizeChange = false

    /**
     * The window changed size under a surface the thread draws on: the EGL surface is made anew, as
     * GLSurfaceView does. A driver may have taken the next buffer at the last swap, of the old size, and a
     * still frame drawn into it stays wrong: a picture turned with the launcher showed in a corner.
     */
    private var windowResized = false

    /** Creating the EGL surface failed; don't retry in a loop, wait for a repair. */
    private var surfaceBroken = false

    /** The EGL surface is the window's, not the pbuffer a thread prepares on. */
    private var onWindowSurface = false

    /** No config with pbuffers, or making one failed: the thread waits for the window instead. */
    private var pbufferBroken = false
    private var configHasPbuffers = false

    /** The render guard hears of failures now: frames are expected. */
    private var guardArmed = false

    private var firstPictureReported = false

    /**
     * Frames swapped to the window so far. The owner's heartbeat gives the first frame after a shader
     * (re)compilation more time: it counts the frames presented since then.
     */
    @Volatile
    var presentedFrames: Long = 0
        private set

    private var gpuReported = false

    /** An EGL surface exists on the window; [onSurfaceDestroyed] can wait for it to go. */
    @Volatile
    private var eglSurfaceAlive = false

    /** The engine's surface was created or changed size. */
    fun onSurfaceCreatedOrChanged(width: Int, height: Int) {
        synchronized(lock) {
            if (hasSurface && !offWindow && (width != surfaceWidth || height != surfaceHeight)) windowResized = true
            hasSurface = true
            offWindow = false
            surfaceWidth = width
            surfaceHeight = height
            pendingSizeChange = true
            renderRequested = true
            lock.notifyAll()
        }
    }

    /**
     * Call from the engine's onSurfaceDestroyed. With [waitMs] > 0 it blocks until the EGL surface is
     * destroyed: the system may free the window right after onSurfaceDestroyed returns.
     */
    fun onSurfaceDestroyed(waitMs: Long = 0) {
        synchronized(lock) {
            hasSurface = false
            lock.notifyAll()
            val deadline = SystemClock.uptimeMillis() + waitMs
            while (waitMs > 0 && eglSurfaceAlive && running && isAlive) {
                val remaining = deadline - SystemClock.uptimeMillis()
                if (remaining <= 0) {
                    Timber.tag(TAG).w("EGL surface not destroyed in $waitMs ms")
                    break
                }
                lock.wait(remaining)
            }
        }
    }

    /**
     * Before the engine has the window: the thread draws [width] x [height] frames into a pbuffer and shows
     * none, until [onSurfaceCreatedOrChanged] brings the window. A frame asked for is drawn even while
     * paused: nothing of it is seen. Call it before the thread starts, so that it picks a config with
     * pbuffers; a device without one just waits for the window.
     */
    fun prepareOffWindow(width: Int, height: Int) {
        synchronized(lock) {
            offWindow = true
            surfaceWidth = width
            surfaceHeight = height
            pendingSizeChange = true
            renderRequested = true
            lock.notifyAll()
        }
    }

    /**
     * The next frame asked for is shown even while paused: a wallpaper that gets the window while hidden
     * still puts its picture there. It stays asked until a frame is really shown.
     */
    fun presentOnce() {
        synchronized(lock) {
            presentRequested = true
            lock.notifyAll()
        }
    }

    fun onPauseRendering() {
        synchronized(lock) {
            paused = true
            lock.notifyAll()
        }
    }

    fun onResumeRendering() {
        synchronized(lock) {
            paused = false
            lock.notifyAll()
        }
    }

    fun requestRender() {
        synchronized(lock) {
            renderRequested = true
            lock.notifyAll()
        }
    }

    /** Runs [task] on the GL thread with the EGL context current. */
    fun post(task: () -> Unit) {
        synchronized(lock) {
            tasks.addLast(task)
            lock.notifyAll()
        }
    }

    /** Whether new frames are expected now (wallpaper visible and video playing); arms the render guard. */
    fun setFramesExpected(expected: Boolean) {
        synchronized(lock) {
            framesExpected = expected
            lock.notifyAll()
        }
    }

    /** Stops the thread. A thread stuck in a driver call is abandoned after [timeoutMs] instead of blocking the caller. */
    fun requestExitAndWait(timeoutMs: Long = EXIT_TIMEOUT_MS) {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        try {
            join(timeoutMs)
        } catch (_: InterruptedException) {
        }
        if (isAlive) Timber.tag(TAG).w("GL thread didn't stop in $timeoutMs ms, abandoning it")
    }

    override fun run() {
        try {
            try {
                initEgl()
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "No OpenGL ES $glesVersion context")
                onInitFailed?.invoke(t)
                return
            }

            while (true) {
                val work = awaitWork() ?: break
                if (work.newSurface != null) {
                    if (work.newSurface == NewSurface.WINDOW) createEglSurface() else createPbufferSurface()
                    continue
                }
                work.tasks.forEach { task ->
                    runCatching(task).onFailure { Timber.tag(TAG).e(it, "GL task failed") }
                }
                if (work.draw) drawFrame()
                renderGuard?.check()
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "GL thread error")
        } finally {
            try {
                renderer.dispose()
            } catch (_: Throwable) {
            }
            destroyEglSurface()
            destroyEgl()
        }
    }

    private enum class NewSurface { WINDOW, PBUFFER }

    /** @param newSurface the EGL surface to make first; the tasks and the frame wait for the next turn then. */
    private class Work(val tasks: List<() -> Unit>, val draw: Boolean, val newSurface: NewSurface? = null)

    /** Waits until there is something to do, or until the render guard or the heartbeat is due. */
    private fun awaitWork(): Work? {
        synchronized(lock) {
            while (running) {
                lastLoopAtMs = nowMs()
                guardArmed = framesExpected && !paused && hasSurface
                renderGuard?.setExpected(guardArmed)

                if (!hasSurface && hasWindowSurface()) {
                    destroyEglSurface()
                    surfaceCreatedDelivered = false
                    lock.notifyAll() // onSurfaceDestroyed may be waiting for this
                }

                val hasSize = surfaceWidth > 0 && surfaceHeight > 0
                val onWindow = hasSurface && hasSize && !surfaceBroken
                val onPbuffer = offWindow && !hasSurface && hasSize && !pbufferBroken
                // Nothing is taken in the turn that makes the surface: if that fails, a task taken would be lost.
                if (onWindow && !hasWindowSurface()) return Work(emptyList(), draw = false, NewSurface.WINDOW)
                if (onWindow && windowResized) {
                    windowResized = false
                    return Work(emptyList(), draw = false, NewSurface.WINDOW)
                }
                if (onPbuffer && !hasEglSurface()) return Work(emptyList(), draw = false, NewSurface.PBUFFER)

                val canRender = onWindow || onPbuffer
                // Off the window nothing is seen, so a pause holds no frame back.
                val mayDraw = onPbuffer || !paused || presentRequested
                val drawDue = canRender && mayDraw && (continuous || renderRequested)
                val tasksDue = canRender && tasks.isNotEmpty()
                if (drawDue || tasksDue) {
                    // When dirty: this frame answers the request.
                    if (drawDue && !continuous) renderRequested = false
                    val drained = if (tasksDue) tasks.toList().also { tasks.clear() } else emptyList()
                    return Work(drained, drawDue)
                }

                val guard = renderGuard
                if (guard != null && guardArmed) {
                    // Wake up for the guard and the heartbeat even when nothing is drawn.
                    lock.wait(guard.nextCheckInMs().coerceAtMost(HEARTBEAT_MS))
                    return Work(emptyList(), draw = false)
                }
                lock.wait()
            }
            return null
        }
    }

    private fun drawFrame() {
        val display = eglDisplay ?: return
        val surface = eglSurface ?: return
        try {
            // The first frame on a new context or surface
            if (!surfaceCreatedDelivered) {
                Timber.d("GLThread: onSurfaceCreated($surfaceWidth x $surfaceHeight)")
                renderer.onSurfaceCreated()
                surfaceCreatedDelivered = true
            }

            if (pendingSizeChange) {
                Timber.d("GLThread: onSurfaceChanged($surfaceWidth x $surfaceHeight)")
                GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
                renderer.onSurfaceChanged(surfaceWidth, surfaceHeight)
                pendingSizeChange = false
            }

            // One frame
            val isNewFrame = renderer.onDrawFrame()
            val hasPicture = renderer.hasPicture

            if (!onWindowSurface) {
                // Nothing to show: only have the driver do now what the first frame on screen would wait for.
                GLES20.glFinish()
                if (hasPicture) reportFirstPicture()
                return
            }
            // Without its picture the frame is empty: the window keeps what it shows.
            if (gated && !hasPicture) return

            // Show it
            if (!EGL14.eglSwapBuffers(display, surface)) {
                val error = EGL14.eglGetError()
                Timber.tag(TAG).w("eglSwapBuffers failed: 0x%x", error)
                renderGuard?.onSwapFailed(error)
                return
            }
            presentedFrames++
            synchronized(lock) { presentRequested = false }
            if (isNewFrame) renderGuard?.onFramePresented()
            if (hasPicture) reportFirstPicture()
        } catch (t: Throwable) {
            val guard = renderGuard ?: throw t
            Timber.tag(TAG).e(t, "Frame failed")
            guard.onDrawFailed()
        }
    }

    private fun reportFirstPicture() {
        if (firstPictureReported) return
        firstPictureReported = true
        onFirstPicture?.invoke()
    }

    /** Repairs requested by the render guard; called from [RenderGuard.check] on this thread. */
    private inner class Repairs : RenderRepairs {

        override fun redraw() = requestRender()

        override fun recreateSurface() {
            destroyEglSurface()
            surfaceBroken = false
            pendingSizeChange = true
            requestRender()
        }

        override fun recreateContext() {
            try {
                renderer.onContextLost()
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onContextLost failed")
            }
            destroyEglSurface()
            destroyEgl()
            initEgl()
            surfaceBroken = false
            surfaceCreatedDelivered = false
            pendingSizeChange = true
            requestRender()
        }
    }

    // ----------------- EGL -----------------

    private fun hasEglSurface() = eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE

    private fun hasWindowSurface() = hasEglSurface() && onWindowSurface

    private fun initEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val display = eglDisplay
        if (display == null || display == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        eglConfig = chooseEglConfig(display)

        val attribList = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, glesVersion,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            display, eglConfig, EGL14.EGL_NO_CONTEXT, attribList, 0
        )
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }
    }

    /** Returns false (and tells the render guard) instead of throwing, so a broken surface can be repaired. */
    private fun createEglSurface(): Boolean {
        val display = eglDisplay
        val config = eglConfig
        if (display == null || config == null) {
            // EGL itself is gone (a failed context recreation); only a new engine can help.
            surfaceBroken = true
            return false
        }
        destroyEglSurface()

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val newSurface = EGL14.eglCreateWindowSurface(
            display, config, surfaceHolder.surface, surfaceAttribs, 0
        )
        if (newSurface == null || newSurface == EGL14.EGL_NO_SURFACE ||
            !EGL14.eglMakeCurrent(display, newSurface, newSurface, eglContext)
        ) {
            val error = EGL14.eglGetError()
            Timber.tag(TAG).w("Failed to create or bind window surface: 0x%x", error)
            if (newSurface != null && newSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, newSurface)
            surfaceBroken = true
            // The guard is deaf while no frames are expected, and a wallpaper that waits for its turn on
            // the window would then wait for good.
            if (gated && !guardArmed) {
                renderGuardListener?.onRenderUnrecoverable(RenderStall.SurfaceLost)
            } else {
                renderGuard?.onSwapFailed(error)
            }
            return false
        }
        eglSurface = newSurface
        onWindowSurface = true
        eglSurfaceAlive = true
        pendingSizeChange = true
        reportGpu()
        return true
    }

    /** A 1 x 1 pbuffer to have the context current on before there is a window. False where there are none. */
    private fun createPbufferSurface(): Boolean {
        val display = eglDisplay
        val config = eglConfig
        if (display == null || config == null || !configHasPbuffers) {
            pbufferBroken = true
            return false
        }
        val attribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val newSurface = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        if (newSurface == null || newSurface == EGL14.EGL_NO_SURFACE ||
            !EGL14.eglMakeCurrent(display, newSurface, newSurface, eglContext)
        ) {
            Timber.tag(TAG).w("No pbuffer to prepare on, waiting for the window: 0x%x", EGL14.eglGetError())
            if (newSurface != null && newSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, newSurface)
            pbufferBroken = true
            return false
        }
        eglSurface = newSurface
        onWindowSurface = false
        pendingSizeChange = true
        reportGpu()
        return true
    }

    /** Every report from here on says which GPU and driver it came from. Needs a current context. */
    private fun reportGpu() {
        if (gpuReported) return
        gpuReported = true
        val renderer = GLES20.glGetString(GLES20.GL_RENDERER)
        val version = GLES20.glGetString(GLES20.GL_VERSION)
        Diagnostics.reporter.setKey("gl_renderer", "$renderer, $version")
    }

    private fun destroyEglSurface() {
        val display = eglDisplay
        val surface = eglSurface
        if (display != null && surface != null && surface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT,
            )
            EGL14.eglDestroySurface(display, surface)
        }
        eglSurface = EGL14.EGL_NO_SURFACE
        onWindowSurface = false
        eglSurfaceAlive = false
    }

    private fun destroyEgl() {
        val display = eglDisplay
        val context = eglContext
        if (display != null) {
            if (context != null && context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context)
            }
            EGL14.eglTerminate(display)
        }
        eglDisplay = null
        eglContext = null
        eglConfig = null
    }

    /** A thread that starts off the window asks for a config good for a pbuffer too, and does without if there is none. */
    private fun chooseEglConfig(display: EGLDisplay): EGLConfig {
        val withPbuffers = if (offWindow) findEglConfig(display, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT) else null
        configHasPbuffers = withPbuffers != null
        return withPbuffers ?: findEglConfig(display, EGL14.EGL_WINDOW_BIT) ?: throw RuntimeException("No configs match")
    }

    private fun findEglConfig(display: EGLDisplay, surfaceTypes: Int): EGLConfig? {
        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, if (glesVersion >= 3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, surfaceTypes,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_NONE,
        )

        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(
                display,
                attribList, 0,
                null, 0, 0,
                numConfigs, 0,
            )
        ) {
            throw RuntimeException("eglChooseConfig failed")
        }

        val configsCount = numConfigs[0]
        if (configsCount <= 0) return null

        val configs = arrayOfNulls<EGLConfig>(configsCount)
        EGL14.eglChooseConfig(
            display,
            attribList, 0,
            configs, 0, configsCount,
            numConfigs, 0,
        )

        return configs[0]
    }

    private companion object {
        const val TAG = "WallpaperGLThread"
        const val GUARD_TAG = "RenderGuard"
        const val HEARTBEAT_MS = 500L
        const val EXIT_TIMEOUT_MS = 1_000L
    }
}
