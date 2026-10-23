package com.onthecrow.wallper.core.blackhole.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleScene
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** Renders a still of the black hole offscreen (EGL pbuffer) into a PNG, for the wallpaper list. */
@Singleton
class BlackHolePreviewRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tablesRepository: LensTablesRepository,
) {
    // EGL contexts are bound to a thread: one thread for every preview.
    private val glDispatcher = Executors.newSingleThreadExecutor { Thread(it, "BlackHolePreview") }.asCoroutineDispatcher()

    /** False if this device has no OpenGL ES 3.0 or the shaders don't build. */
    suspend fun render(params: BlackHoleParams, file: File, width: Int, height: Int): Boolean {
        val quality = QualityLevel.MEDIUM
        val tables = tablesRepository.get(lensTableKey(params, quality))
        return withContext(glDispatcher) {
            runCatching { renderOffscreen(params, quality, tables, file, width, height) }
                .onFailure { Timber.tag(TAG).e(it, "Preview failed") }
                .getOrDefault(false)
        }
    }

    private fun renderOffscreen(
        params: BlackHoleParams,
        quality: QualityLevel,
        tables: com.onthecrow.wallper.core.blackhole.physics.LensTables,
        file: File,
        width: Int,
        height: Int,
    ): Boolean {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val config = chooseConfig(display)
        val eglContext = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "No ES 3.0 context" }
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        try {
            check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) { "eglMakeCurrent" }
            val renderer = BlackHoleRenderer(context.assets).apply {
                fadeIn = false
                setQuality(quality)
                setParams(params)
                setTables(tables)
                onSurfaceSize(width, height)
            }
            val output = checkNotNull(RenderTarget.create(width, height, TargetFormat.RGBA8)) { "No RGBA8 target" }
            try {
                val frame = BlackHoleScene(params).frame(0)
                if (!renderer.render(frame, nowNanos = 0, outputFramebuffer = output.framebuffer)) {
                    renderer.failure?.let { throw it }
                    return false
                }
                val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, output.framebuffer)
                GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                save(pixels, width, height, file)
                return true
            } finally {
                output.release()
                renderer.release()
            }
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglReleaseThread()
        }
    }

    private fun save(pixels: ByteBuffer, width: Int, height: Int, file: File) {
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        pixels.position(0)
        raw.copyPixelsFromBuffer(pixels)
        // GL rows start at the bottom.
        val flipped = Bitmap.createBitmap(raw, 0, 0, width, height, Matrix().apply { preScale(1f, -1f) }, false)
        raw.recycle()
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        temp.outputStream().use { flipped.compress(Bitmap.CompressFormat.PNG, 100, it) }
        flipped.recycle()
        temp.renameTo(file)
    }

    private fun chooseConfig(display: android.opengl.EGLDisplay): EGLConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No ES 3.0 config" }
        return configs[0]!!
    }

    private companion object {
        const val TAG = "BlackHole"
    }
}
