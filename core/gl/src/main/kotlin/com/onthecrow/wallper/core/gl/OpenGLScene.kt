package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import timber.log.Timber

/** The shader program and crop geometry shared by all video slots. GL thread only. */
internal class OpenGLScene(
    private val sceneWidth: Int,
    private val sceneHeight: Int,
    private val mediaInfo: MediaInfo,
    rect: CropRect,
) {
    private val programId: Int
    private val uMVPMatrixHandle: Int
    private val uTexMatrixHandle: Int
    private val uUvShiftHandle: Int
    private val aPositionHandle: Int
    private val aTextureCoordHandler: Int

    private val vertexShader = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uTexMatrix;
        uniform vec2 uUvShift;
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;

        void main() {
            gl_Position = uMVPMatrix * aPosition;
            // The shift moves the crop window in the upright frame, before the buffer's own transform.
            vTextureCoord = (uTexMatrix * (aTextureCoord + vec4(uUvShift, 0.0, 0.0))).xy;
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

    private val uvCoords = UVCoords.fromRect(mediaInfo.displayWidth, mediaInfo.displayHeight, rect)
    private val verticesData = getVerticesData(uvCoords)

    init {
        GLES20.glViewport(0, 0, sceneWidth, sceneHeight)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_BLEND) // nothing here is translucent
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

        uUvShiftHandle = GLES20.glGetUniformLocation(programId, "uUvShift")
        checkGlError("glGetUniformLocation uUvShift")
        if (uUvShiftHandle == -1) {
            throw RuntimeException("Could not get attrib location for uUvShift")
        }

        aTextureCoordHandler = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        checkGlError("glGetAttribLocation aTextureCoord")
        if (aTextureCoordHandler == -1) {
            throw RuntimeException("Could not get attrib location for aTextureCoord")
        }
    }

    fun createTexture() = OpenGLExternalTexture(
        textureWidth = sceneWidth,
        textureHeight = sceneHeight,
        verticesData = verticesData,
    )

    /** @param deleteProgram false when the EGL context is already gone. */
    fun release(deleteProgram: Boolean = true) {
        if (deleteProgram) GLES20.glDeleteProgram(programId)
    }

    /** @param pageOffset the launcher's page offset to pan the crop with, or null to show it as chosen. */
    fun draw(texture: OpenGLExternalTexture, pageOffset: Float?) {
        GLES20.glClearColor(0f, 0f, 0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programId)
        checkGlError("glUseProgram")

        val shiftU = pageOffset?.let { PageScroll.shiftU(uvCoords.topLeft.u, uvCoords.topRight.u, it) } ?: 0f
        GLES20.glUniform2f(uUvShiftHandle, shiftU, 0f)

        texture.draw(
            aPositionHandle = aPositionHandle,
            aTextureCoordHandler = aTextureCoordHandler,
            uTexHandler = uTexMatrixHandle,
            uMvpHandler = uMVPMatrixHandle,
        )
    }

    private fun getVerticesData(uvCoords: UVCoords): FloatArray {
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