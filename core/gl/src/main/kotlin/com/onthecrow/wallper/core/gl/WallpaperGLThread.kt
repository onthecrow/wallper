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
import com.onthecrow.wallper.core.gl.guard.RenderGuard
import com.onthecrow.wallper.core.gl.guard.RenderRepairs
import timber.log.Timber

/**
 * Собственный GL-поток, который работает напрямую с SurfaceHolder от WallpaperService.Engine
 * и вызывает [GLWallpaperRenderer].
 *
 * With a [renderGuardListener] the thread owns a [RenderGuard]: swap failures and exceptions are repaired
 * here (new EGL surface or context), and what can't be repaired is reported to the listener.
 * [lastLoopAtMs] (in [nowMs] time, elapsedRealtime by default) lets the owner notice a thread that hangs
 * inside a driver call, which no in-thread check can.
 *
 * @param glesVersion 2 or 3. If the device can't create such a context, [onInitFailed] is called and the
 * thread ends.
 */
class WallpaperGLThread(
    private val surfaceHolder: SurfaceHolder,
    private val renderer: GLWallpaperRenderer,
    private val continuous: Boolean = false,
    renderGuardListener: RenderGuard.Listener? = null,
    private val nowMs: () -> Long = SystemClock::elapsedRealtime,
    private val glesVersion: Int = 2,
    private val onInitFailed: ((Throwable) -> Unit)? = null,
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

    private val lock = Object()

    // Guarded by lock
    private val tasks = ArrayDeque<() -> Unit>()
    private var framesExpected = false

    /** When the render loop last ran. Stops moving if the thread hangs or dies. */
    @Volatile
    var lastLoopAtMs: Long = nowMs()
        private set

    private val renderGuard = renderGuardListener?.let { listener ->
        RenderGuard(Repairs(), listener, nowMs) { Timber.tag(TAG).d(it) }
    }

    // EGL объекты
    private var eglDisplay: EGLDisplay? = null
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // флаги для вызова onSurfaceCreated/onSurfaceChanged
    private var surfaceCreatedDelivered = false
    private var pendingSizeChange = false

    /** Creating the EGL surface failed; don't retry in a loop, wait for a repair. */
    private var surfaceBroken = false

    /**
     * Frames swapped to the window so far. The owner's heartbeat gives the first frame after a shader
     * (re)compilation more time: it counts the frames presented since then.
     */
    @Volatile
    var presentedFrames: Long = 0
        private set

    /** An EGL surface exists on the window; [onSurfaceDestroyed] can wait for it to go. */
    @Volatile
    private var eglSurfaceAlive = false

    /**
     * Вызывай, когда у Engine появился или изменился surface
     */
    fun onSurfaceCreatedOrChanged(width: Int, height: Int) {
        synchronized(lock) {
            hasSurface = true
            surfaceWidth = width
            surfaceHeight = height
            pendingSizeChange = true
            renderRequested = true
            lock.notifyAll()
        }
    }

    /**
     * Вызывай из onSurfaceDestroyed Engine. With [waitMs] > 0 it blocks until the EGL surface is
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
                if (work.needsSurface && !createEglSurface()) continue
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

    private class Work(val tasks: List<() -> Unit>, val draw: Boolean, val needsSurface: Boolean)

    /** Waits until there is something to do, or until the render guard or the heartbeat is due. */
    private fun awaitWork(): Work? {
        synchronized(lock) {
            while (running) {
                lastLoopAtMs = nowMs()
                val guardArmed = framesExpected && !paused && hasSurface
                renderGuard?.setExpected(guardArmed)

                if (!hasSurface && hasEglSurface()) {
                    destroyEglSurface()
                    surfaceCreatedDelivered = false
                    lock.notifyAll() // onSurfaceDestroyed may be waiting for this
                }

                val canRender = hasSurface && surfaceWidth > 0 && surfaceHeight > 0 && !surfaceBroken
                val needsSurface = canRender && !hasEglSurface()
                val drawDue = canRender && !paused && (continuous || renderRequested)
                val tasksDue = canRender && tasks.isNotEmpty()
                if (needsSurface || drawDue || tasksDue) {
                    // если режим "when dirty" — съедаем запрос
                    if (drawDue && !continuous) renderRequested = false
                    val drained = if (tasksDue) tasks.toList().also { tasks.clear() } else emptyList()
                    return Work(drained, drawDue, needsSurface)
                }

                val guard = renderGuard
                if (guard != null && guardArmed) {
                    // Wake up for the guard and the heartbeat even when nothing is drawn.
                    lock.wait(guard.nextCheckInMs().coerceAtMost(HEARTBEAT_MS))
                    return Work(emptyList(), draw = false, needsSurface = false)
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
            // первый вход после создания контекста/поверхности
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

            // один кадр
            val isNewFrame = renderer.onDrawFrame()

            // вывод кадра на экран
            if (!EGL14.eglSwapBuffers(display, surface)) {
                val error = EGL14.eglGetError()
                Timber.tag(TAG).w("eglSwapBuffers failed: 0x%x", error)
                renderGuard?.onSwapFailed(error)
                return
            }
            presentedFrames++
            if (isNewFrame) renderGuard?.onFramePresented()
        } catch (t: Throwable) {
            val guard = renderGuard ?: throw t
            Timber.tag(TAG).e(t, "Frame failed")
            guard.onDrawFailed()
        }
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
            renderGuard?.onSwapFailed(error)
            return false
        }
        eglSurface = newSurface
        eglSurfaceAlive = true
        pendingSizeChange = true
        return true
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

    private fun chooseEglConfig(display: EGLDisplay): EGLConfig {
        val attribList = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, if (glesVersion >= 3) EGLExt.EGL_OPENGL_ES3_BIT_KHR else EGL14.EGL_OPENGL_ES2_BIT,
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
        if (configsCount <= 0) {
            throw RuntimeException("No configs match")
        }

        val configs = arrayOfNulls<EGLConfig>(configsCount)
        EGL14.eglChooseConfig(
            display,
            attribList, 0,
            configs, 0, configsCount,
            numConfigs, 0,
        )

        return configs[0]!!
    }

    private companion object {
        const val TAG = "RenderGuard"
        const val HEARTBEAT_MS = 500L
        const val EXIT_TIMEOUT_MS = 1_000L
    }
}
