package com.onthecrow.wallper.gl

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import com.onthecrow.wallper.util.GLErrorUtils
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


@SuppressLint("Recycle")
class OpenGLExternalTexture(
    val textureWidth: Int,
    val textureHeight: Int,
    verticesData: FloatArray,
    externalTextureId: Int? = null,
    var rotate: Int = Surface.ROTATION_0
) {
    private val mMVPMatrix = FloatArray(16)
    private val mTexMatrix = FloatArray(16)
    private var verticesBuffer: FloatBuffer

    // ---- для логики «нет новых кадров» ----
    private var lastFrameTsNs: Long = -1L              // последний timestamp кадра из SurfaceTexture (наносекунды)
    private var lastFrameAvailMs: Long = 0L            // когда пришёл onFrameAvailable (elapsedRealtime)
    private var lastUpdateTexMs: Long = 0L             // когда последний раз вызывали updateTexImage()
    private var stallStartMs: Long = 0L                // когда впервые заметили, что кадры не меняются
    private var lastNoFrameLogMs: Long = 0L            // когда последний раз логировали стазис

    private var textureId: Int = -1

    @Volatile private var onFrameAvailableListener: (() -> Unit)? = null

    private var surfaceTexture: SurfaceTexture
    var surface: Surface

    init {
        Matrix.setIdentityM(mTexMatrix, 0)
        Matrix.setIdentityM(mMVPMatrix, 0)

        verticesBuffer = ByteBuffer.allocateDirect(
            verticesData.size * FLOAT_SIZE_BYTES
        ).order(
            ByteOrder.nativeOrder()
        ).asFloatBuffer().also {
            it.put(verticesData).position(0)
        }

        textureId = externalTextureId
            ?: IntArray(1).also {
                GLES20.glGenTextures(1, it, 0)
            }.let { it[0] }

        GLErrorUtils.checkGlError("glBindTexture textureId")

        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )

        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(textureWidth, textureHeight)

        surface = Surface(surfaceTexture)
    }

    fun setVerticesData(data: FloatArray) {
        verticesBuffer = ByteBuffer.allocateDirect(
            data.size * FLOAT_SIZE_BYTES
        ).order(
            ByteOrder.nativeOrder()
        ).asFloatBuffer().also {
            it.put(data).position(0)
        }
    }

    fun release() {
        verticesBuffer.clear()
        surfaceTexture.release()
        surface.release()
    }

    private var lastFrameTs = 0L

    fun attachFrameListener(glView: GLSurfaceView) {
        surfaceTexture.setOnFrameAvailableListener({
            lastFrameAvailMs = SystemClock.elapsedRealtime()
            glView.requestRender()
        }, Handler(Looper.getMainLooper()))
    }

    fun doOnNextUpdateFrame(action: () -> Unit) {
        onFrameAvailableListener = action
    }

    fun updateFrame(
        aPositionHandle: Int,
        aTextureCoordHandler: Int,
        uTexHandler: Int,
        uMvpHandler: Int,
    ) {
        onFrameAvailableListener?.invoke()
        onFrameAvailableListener = null

        GLES20.glViewport(0, 0, textureWidth, textureHeight)

        surfaceTexture.updateTexImage()

        val ts = surfaceTexture.timestamp
        if (ts != lastFrameTs) {
            if (SystemClock.elapsedRealtime() - lastFrameAvailMs > 200) {
                Timber.w("Frame updated late: ${(SystemClock.elapsedRealtime()-lastFrameAvailMs)}ms since onFrameAvailable")
            }
            lastFrameTs = ts
            stallStartMs = 0L
        } else {
            // Раз в ~2с можно писать, если подряд нет обновлений:
            maybeLogNoNewFrames()
        }

        surfaceTexture.getTransformMatrix(mTexMatrix)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        verticesBuffer.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            verticesBuffer
        )

        GLErrorUtils.checkGlError("glVertexAttribPointer aPositionHandle")
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLErrorUtils.checkGlError("glEnableVertexAttribArray aPositionHandle")

        verticesBuffer.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(
            aTextureCoordHandler,
            2,
            GLES20.GL_FLOAT,
            false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES,
            verticesBuffer
        )
        GLErrorUtils.checkGlError("glVertexAttribPointer aTextureCoordHandler")
        GLES20.glEnableVertexAttribArray(aTextureCoordHandler)
        GLErrorUtils.checkGlError("glEnableVertexAttribArray aTextureCoordHandler")

        Matrix.setIdentityM(mMVPMatrix, 0)
        Matrix.setIdentityM(mTexMatrix, 0)

        when (rotate) {
            Surface.ROTATION_90 -> {
                Matrix.rotateM(mTexMatrix, 0, 90f, 0f, 0f, 1f)
                Matrix.translateM(mTexMatrix, 0, 0f, -1f, 0f)
            }

            Surface.ROTATION_180 -> {
                Matrix.rotateM(mTexMatrix, 0, 180f, 0f, 0f, 1f)
                Matrix.translateM(mTexMatrix, 0, -1f, -1f, 0f)
            }

            Surface.ROTATION_270 -> {
                Matrix.rotateM(mTexMatrix, 0, 270f, 0f, 0f, 1f)
                Matrix.translateM(mTexMatrix, 0, -1f, 0f, 0f)
            }

            else -> {}
        }

        GLES20.glUniformMatrix4fv(uMvpHandler, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexHandler, 1, false, mTexMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLErrorUtils.checkGlError("glDrawArrays")
    }

    fun createTexture() {
        surfaceTexture.release()
        surface.release()
        surfaceTexture = SurfaceTexture(textureId)
//        surfaceTexture.setDefaultBufferSize(textureWidth, textureHeight)
        surface = Surface(surfaceTexture)
    }

    /** Логирует, если долго нет новых кадров (throttle: сначала каждые 2с, потом каждые 10с) */
    private fun maybeLogNoNewFrames(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (stallStartMs == 0L) {
            // начали отсчёт «застоя»
            stallStartMs = nowMs
            lastNoFrameLogMs = 0L
            return
        }

        val sinceStall = nowMs - stallStartMs
        val sinceAvail = if (lastFrameAvailMs == 0L) -1L else nowMs - lastFrameAvailMs
        val sinceUpdate = if (lastUpdateTexMs == 0L) -1L else nowMs - lastUpdateTexMs

        // первые 10 секунд — лог раз в 2с; дальше — раз в 10с
        val interval = if (sinceStall < 10_000L) 2_000L else 10_000L
        if (nowMs - lastNoFrameLogMs < interval) return
        lastNoFrameLogMs = nowMs

        Timber.w(
            "No new frames for ${sinceStall}ms " +
                    "(since onFrameAvailable=${sinceAvail}ms, " +
                    "since updateTexImage=${sinceUpdate}ms, " +
                    "lastTsNs=$lastFrameTsNs)"
        )
    }

    companion object {
        private const val TRIANGLE_VERTICES_DATA_STRIDE = 5
        const val FLOAT_SIZE_BYTES = 4
        const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES =
            TRIANGLE_VERTICES_DATA_STRIDE * FLOAT_SIZE_BYTES
        const val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
        const val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3
    }
}