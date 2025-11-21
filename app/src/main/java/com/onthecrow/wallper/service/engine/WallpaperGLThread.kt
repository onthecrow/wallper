package com.onthecrow.wallper.service.engine

import com.onthecrow.wallper.service.renderer.GLWallpaperRenderer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.SurfaceHolder
import timber.log.Timber
import javax.microedition.khronos.opengles.GL10

/**
 * Собственный GL-поток, который работает напрямую с SurfaceHolder от WallpaperService.Engine.
 * Он вызывает методы GLSurfaceView.Renderer (через твой GLWallpaperRenderer).
 */
class WallpaperGLThread(
    private val surfaceHolder: SurfaceHolder,
    private val renderer: GLWallpaperRenderer,
    private val continuous: Boolean = false,
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

    // EGL объекты
    private var eglDisplay: EGLDisplay? = null
    private var eglConfig: EGLConfig? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    // флаги для вызова onSurfaceCreated/onSurfaceChanged
    private var surfaceCreatedDelivered = false
    private var pendingSizeChange = false

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
     * Вызывай из onSurfaceDestroyed Engine
     */
    fun onSurfaceDestroyed() {
        synchronized(lock) {
            hasSurface = false
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

    fun requestExitAndWait() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
        try {
            join()
        } catch (_: InterruptedException) {
        }
    }

    fun requestRender() {
        synchronized(lock) {
            renderRequested = true
            lock.notifyAll()
        }
    }

    override fun run() {
        try {
            initEgl()

            while (true) {
                // Ждём, когда можно рисовать
                synchronized(lock) {
                    while (running &&
                        (paused || !hasSurface || surfaceWidth == 0 || surfaceHeight == 0 ||
                                (!continuous && !renderRequested))
                    ) {
                        if (!hasSurface && eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
                            destroyEglSurface()
                            surfaceCreatedDelivered = false
                        }
                        lock.wait()
                    }

                    if (!running) break

                    if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
                        createEglSurface()
                        surfaceCreatedDelivered = false
                        pendingSizeChange = true
                    }

                    // если режим "when dirty" — съедаем запрос
                    if (!continuous) {
                        renderRequested = false
                    }
                }

                val display = eglDisplay ?: break
                val surface = eglSurface ?: break

                // первый вход после создания контекста/поверхности
                if (!surfaceCreatedDelivered) {
                    Timber.d("GLThread: onSurfaceCreated($surfaceWidth x $surfaceHeight)")
                    // твой рендерер ожидает (GL10?, EGLConfig?), можно передать null
                    renderer.onSurfaceCreated(null as GL10?, null)
                    surfaceCreatedDelivered = true
                }

                if (pendingSizeChange) {
                    Timber.d("GLThread: onSurfaceChanged($surfaceWidth x $surfaceHeight)")
                    GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
                    renderer.onSurfaceChanged(null as GL10?, surfaceWidth, surfaceHeight)
                    pendingSizeChange = false
                }

                // один кадр
                renderer.onDrawFrame(null as GL10?)

                // вывод кадра на экран
                EGL14.eglSwapBuffers(display, surface)

//                // простое ограничение fps ~60
//                try {
//                    sleep(16)
//                } catch (_: InterruptedException) {
//                }
            }
        } catch (t: Throwable) {
            Timber.e(t, "GL thread error")
        } finally {
            try {
                renderer.dispose()
            } catch (_: Throwable) {
            }
            destroyEglSurface()
            destroyEgl()
        }
    }

    // ----------------- EGL -----------------

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
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(
            display, eglConfig, EGL14.EGL_NO_CONTEXT, attribList, 0
        )
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }
    }

    private fun createEglSurface() {
        val display = eglDisplay ?: return
        val config = eglConfig ?: return
        destroyEglSurface()

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            display, config, surfaceHolder.surface, surfaceAttribs, 0
        )
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create window surface")
        }

        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("Failed to make EGL context current")
        }
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
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
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
}
