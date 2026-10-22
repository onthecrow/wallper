package com.onthecrow.wallper.core.gl

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** An OES texture with the SurfaceTexture and Surface a player decodes into. GL thread only. */
@SuppressLint("Recycle")
internal class OpenGLExternalTexture(
    private val textureWidth: Int,
    private val textureHeight: Int,
    verticesData: FloatArray,
) {
    private val mMVPMatrix = FloatArray(16)
    private val mTexMatrix = FloatArray(16)
    private val verticesBuffer: FloatBuffer = ByteBuffer.allocateDirect(
        verticesData.size * FLOAT_SIZE_BYTES
    ).order(
        ByteOrder.nativeOrder()
    ).asFloatBuffer().also {
        it.put(verticesData).position(0)
    }

    private val textureId: Int
    private val surfaceTexture: SurfaceTexture
    val surface: Surface

    private var lastFrameTimestampNs = 0L

    init {
        Matrix.setIdentityM(mTexMatrix, 0)
        Matrix.setIdentityM(mMVPMatrix, 0)

        textureId = IntArray(1).also {
            GLES20.glGenTextures(1, it, 0)
        }.let { it[0] }
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
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
        surface = Surface(surfaceTexture)
    }

    fun setFrameListener(handler: Handler, listener: () -> Unit) {
        surfaceTexture.setOnFrameAvailableListener({ listener() }, handler)
    }

    /**
     * Latches the newest queued image. Called on every draw, even without a frame callback: a SurfaceTexture
     * stops calling onFrameAvailable while a frame sits unlatched, and this re-arms it.
     *
     * @return true if the image differs from the previously latched one.
     */
    fun latch(): Boolean {
        surfaceTexture.updateTexImage()
        val timestampNs = surfaceTexture.timestamp
        val isNewFrame = timestampNs != 0L && timestampNs != lastFrameTimestampNs
        lastFrameTimestampNs = timestampNs
        return isNewFrame
    }

    /**
     * @param deleteTexture false when the EGL context is already gone: the texture name may then belong
     * to an object of the new context.
     */
    fun release(deleteTexture: Boolean = true) {
        surfaceTexture.setOnFrameAvailableListener(null)
        surfaceTexture.release()
        surface.release()
        if (deleteTexture) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
    }

    fun draw(
        aPositionHandle: Int,
        aTextureCoordHandler: Int,
        uTexHandler: Int,
        uMvpHandler: Int,
    ) {
        GLES20.glViewport(0, 0, textureWidth, textureHeight)

        // Maps upright frame coordinates onto the decoder's buffer: the video's rotation (the decoder
        // applies it through the buffer transform), the vertical flip and the decoder's own crop.
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

        GLES20.glUniformMatrix4fv(uMvpHandler, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexHandler, 1, false, mTexMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLErrorUtils.checkGlError("glDrawArrays")
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
