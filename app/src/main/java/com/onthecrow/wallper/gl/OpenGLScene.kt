package com.onthecrow.wallper.gl

import android.graphics.Rect
import android.opengl.GLES20
import com.onthecrow.wallper.service.VideoMetadata
import com.onthecrow.wallper.util.ShaderUtils
import timber.log.Timber

class OpenGLScene(
    sceneWidth: Int,
    sceneHeight: Int,
    videoMetadata: VideoMetadata,
    rect: Rect,
    fullscreenTextureId: Int? = null,
) {
    private val programId: Int
    private val uMVPMatrixHandle: Int
    private val uTexMatrixHandle: Int
    private val aPositionHandle: Int
    private val aTextureCoordHandler: Int

    private val vertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uTexMatrix;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;

        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTextureCoord = (uTexMatrix * aTextureCoord).xy;
        }
    """.trimIndent()

    private val fragmentShader = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform samplerExternalOES sTexture;

        void main() {
            gl_FragColor = texture2D(sTexture, vTextureCoord);
        }
    """.trimIndent()

    val fullscreenTexture = OpenGLExternalTexture(
        textureWidth = sceneWidth,
        textureHeight = sceneHeight,
        verticesData = getVerticesData(videoMetadata, rect, videoMetadata.rotation),
        externalTextureId = fullscreenTextureId,
        rotate = videoMetadata.rotation,
    )

    init {
        GLES20.glViewport(0, 0, sceneWidth, sceneHeight)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)    // если не рисуете полупрозрачность
        GLES20.glDisable(GLES20.GL_DITHER)

        programId = ShaderUtils.createProgram(vertexShader, fragmentShader)
        if (programId == 0) {
            throw java.lang.RuntimeException("Could not create shader program")
        }

        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        checkGlError("glGetAttribLocation aPosition")
        if (aPositionHandle == -1) {
            throw RuntimeException("Could not get attrib location for aPosition")
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(programId, "uMVPMatrix")
        checkGlError("glGetUniformLocation uMVPMatrix")
        if (uMVPMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uMVPMatrix")
        }

        uTexMatrixHandle = GLES20.glGetUniformLocation(programId, "uTexMatrix")
        checkGlError("glGetUniformLocation uTexMatrix")
        if (uTexMatrixHandle == -1) {
            throw RuntimeException("Could not get attrib location for uTexMatrix")
        }

        aTextureCoordHandler = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        checkGlError("glGetAttribLocation aTextureCoord")
        if (aTextureCoordHandler == -1) {
            throw RuntimeException("Could not get attrib location for aTextureCoord")
        }
    }

    fun release() {
        fullscreenTexture.release()
    }

    fun updateFrame() {
        GLES20.glClearColor(
            Math.random().toFloat(),
            Math.random().toFloat(),
            Math.random().toFloat(),
            1.0f
        )
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programId)
        checkGlError("glUseProgram")

        fullscreenTexture.updateFrame(
            aPositionHandle = aPositionHandle,
            aTextureCoordHandler = aTextureCoordHandler,
            uTexHandler = uTexMatrixHandle,
            uMvpHandler = uMVPMatrixHandle,
        )
    }

    fun updateTextureParams(videoMetadata: VideoMetadata, rect: Rect, rotation: Int) {
        fullscreenTexture.setVerticesData(getVerticesData(videoMetadata, rect, rotation))
        fullscreenTexture.rotate = videoMetadata.rotation
    }

    private fun getVerticesData(videoMetadata: VideoMetadata, rect: Rect, rotation: Int): FloatArray {
        val uvCoords = UVCoords.fromRect(videoMetadata.width, videoMetadata.height, rect, rotation)
        return floatArrayOf(
            // X,   Y,      Z,     U,  V
            -1.0f, -1.0f, 1.0f, uvCoords.bottomLeft.u, uvCoords.bottomLeft.v,
            1.0f, -1.0f, 1.0f, uvCoords.bottomRight.u, uvCoords.bottomRight.v,
            -1.0f, 1.0f, 1.0f, uvCoords.topLeft.u, uvCoords.topLeft.v,
            1.0f, 1.0f, 1.0f, uvCoords.topRight.u, uvCoords.topRight.v,
        )
    }

    private fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Timber.e("$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }
}