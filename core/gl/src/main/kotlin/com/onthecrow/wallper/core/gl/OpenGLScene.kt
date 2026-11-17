package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.Tint
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * The shader program, crop geometry and tint of a video. The window it shows is the [framing]'s for the
 * screen's proportions, chosen again whenever the screen changes size ([resize]): a launcher that turns gets
 * the crop made for its orientation, or one derived from the other, never a stretched one. GL thread only.
 */
internal class OpenGLScene(
    private val mediaInfo: MediaInfo,
    private val framing: Framing,
    width: Int,
    height: Int,
    private val tint: Tint? = null,
) {
    private val programId: Int
    private val uMVPMatrixHandle: Int
    private val uTexMatrixHandle: Int
    private val uUvShiftHandle: Int
    private val uTintHandle: Int
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
        ${TintUniform.DECLARATION}

        void main() {
            vec4 color = texture2D(sTexture, vTextureCoord);
            gl_FragColor = ${TintUniform.APPLIED};
        }
    """.trimIndent()

    private var viewportWidth = width
    private var viewportHeight = height

    /** X, Y, Z, U, V of the four corners, for a triangle strip. */
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(VERTEX_COUNT * VERTEX_FLOATS * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    private lateinit var pageScroll: PageScroll

    init {
        resize(width, height)
        GLES20.glViewport(0, 0, width, height)

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

        uTintHandle = GLES20.glGetUniformLocation(programId, TintUniform.NAME)
        checkGlError("glGetUniformLocation ${TintUniform.NAME}")
        if (uTintHandle == -1) {
            throw RuntimeException("Could not get uniform location for ${TintUniform.NAME}")
        }

        aTextureCoordHandler = GLES20.glGetAttribLocation(programId, "aTextureCoord")
        checkGlError("glGetAttribLocation aTextureCoord")
        if (aTextureCoordHandler == -1) {
            throw RuntimeException("Could not get attrib location for aTextureCoord")
        }
    }

    fun createTexture() = OpenGLExternalTexture()

    /** The screen is [width] x [height] now: the framing's window for it, panned from there. */
    fun resize(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        showWindow(framing.forScreen(width, height, mediaInfo.displayWidth, mediaInfo.displayHeight))
    }

    private fun showWindow(window: CropRect) {
        val uv = UVCoords.fromRect(mediaInfo.displayWidth, mediaInfo.displayHeight, window)
        vertices.position(0)
        vertices.put(getVerticesData(uv)).position(0)
        pageScroll = PageScroll(mediaInfo.displayWidth, mediaInfo.displayHeight, window)
    }

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

        val shift = pageOffset?.let(pageScroll::shift)
        GLES20.glUniform2f(uUvShiftHandle, shift?.u ?: 0f, shift?.v ?: 0f)
        TintUniform.set(uTintHandle, tint)

        texture.draw(
            aPositionHandle = aPositionHandle,
            aTextureCoordHandler = aTextureCoordHandler,
            uTexHandler = uTexMatrixHandle,
            uMvpHandler = uMVPMatrixHandle,
            vertices = vertices,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
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

    private companion object {
        const val VERTEX_COUNT = 4
        const val VERTEX_FLOATS = 5
        const val FLOAT_BYTES = 4
    }
}
