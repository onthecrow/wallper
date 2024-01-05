package com.onthecrow.wallper.gl

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import com.onthecrow.wallper.util.GLErrorUtils
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

    private var textureId: Int = -1

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

    fun updateFrame(
        aPositionHandle: Int,
        aTextureCoordHandler: Int,
        uTexHandler: Int,
        uMvpHandler: Int,
    ) {
        GLES20.glViewport(0, 0, textureWidth, textureHeight)

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

        surfaceTexture.updateTexImage()
    }

    fun createTexture() {
        surfaceTexture.release()
        surface.release()
        surfaceTexture = SurfaceTexture(textureId)
        surfaceTexture.setDefaultBufferSize(textureWidth, textureHeight)
        surface = Surface(surfaceTexture)
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