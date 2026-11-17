package com.onthecrow.wallper.core.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import com.onthecrow.wallper.core.model.CropRect
import timber.log.Timber
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws a still picture cropped like a video: the crop's corners as texture coordinates ([UVCoords], turned
 * with the crop), the window panned with the launcher's pages ([PageScroll]). Only the part the window can
 * ever show is decoded ([PictureRegion]), subsampled as far as the screen allows, and only while a context
 * lives: the bitmap goes once it is a texture, and is decoded again after a lost context. The picture is
 * decoded as stored, without its EXIF orientation, as the editor shows it and its crop is measured.
 *
 * A still frame: it draws when asked to, and says it put no new frame on screen. GL methods run on the GL
 * thread; so does [onFailed], once, when the picture can't be decoded.
 *
 * @param frameWidth the picture's width as stored, [frameHeight] its height; [crop] is in its pixels.
 * @param screenWidth the surface's size until the GL thread reports it, [screenHeight] too.
 */
class PictureWallpaperRenderer(
    private val path: String,
    frameWidth: Int,
    frameHeight: Int,
    private val crop: CropRect,
    private var screenWidth: Int,
    private var screenHeight: Int,
    private val onFailed: (Throwable?) -> Unit,
) : GLWallpaperRenderer() {

    /**
     * Where the launcher is between its pages, 0 (the first) to 1 (the last), or null to show the crop as
     * chosen ([PageScroll]). Any thread; request a render after it.
     */
    @Volatile
    var pageOffset: Float? = null

    private val region = PictureRegion(frameWidth, frameHeight, crop)
    private val pageScroll = PageScroll(frameWidth, frameHeight, crop)
    private val textureMatrix = region.textureMatrix()
    private val quad: FloatBuffer = quadOf(UVCoords.fromRect(frameWidth, frameHeight, crop))

    private var program = 0
    private var textureId = 0
    private var failed = false

    override fun onSurfaceCreated() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisable(GLES20.GL_DITHER)
        program = ShaderUtils.createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) throw IllegalStateException("Could not create the picture's shader program")
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
    }

    override fun onDrawFrame(): Boolean {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        // The texture waits for the surface's size: it decides how far to subsample.
        if (textureId == 0 && !failed) loadTexture()
        if (textureId == 0 || program == 0) return false

        GLES20.glUseProgram(program)
        val shift = pageOffset?.let(pageScroll::shift)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uUvShift"), shift?.u ?: 0f, shift?.v ?: 0f)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, textureMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0)

        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val textureCoord = GLES20.glGetAttribLocation(program, "aTextureCoord")
        quad.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, quad)
        GLES20.glEnableVertexAttribArray(position)
        quad.position(2)
        GLES20.glVertexAttribPointer(textureCoord, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, quad)
        GLES20.glEnableVertexAttribArray(textureCoord)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(textureCoord)
        return false
    }

    /** The context is gone, and its objects with it: only forget their names. */
    override fun onContextLost() {
        textureId = 0
        program = 0
    }

    override fun dispose() {
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        textureId = 0
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
    }

    private fun loadTexture() {
        val maxTextureSize = IntArray(1).also { GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, it, 0) }[0]
        val bitmap = try {
            decode(region.sampleSize(crop, screenWidth, screenHeight, maxTextureSize.coerceAtLeast(MIN_TEXTURE_SIZE)))
        } catch (e: Exception) {
            fail(e)
            return
        } catch (e: OutOfMemoryError) {
            fail(e)
            return
        }
        if (bitmap == null) {
            fail(null)
            return
        }
        val ids = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        Timber.d("Picture texture %d x %d", bitmap.width, bitmap.height)
        bitmap.recycle()
    }

    private fun fail(error: Throwable?) {
        failed = true
        onFailed(error)
    }

    /** The region at [sample]: decoded alone where the format allows, else cut from the whole picture. */
    private fun decode(sample: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val rect = Rect(region.left, region.top, region.right, region.bottom)
        val decoder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(path)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(path, false)
            }
        } catch (e: IOException) {
            Timber.w(e, "No region decoder for the picture: decoding all of it")
            null
        }
        if (decoder != null) {
            return try {
                decoder.decodeRegion(rect, options)
            } finally {
                decoder.recycle()
            }
        }
        val whole = BitmapFactory.decodeFile(path, options) ?: return null
        val left = (rect.left / sample).coerceIn(0, whole.width - 1)
        val top = (rect.top / sample).coerceIn(0, whole.height - 1)
        val part = Bitmap.createBitmap(
            whole,
            left,
            top,
            (rect.width() / sample).coerceIn(1, whole.width - left),
            (rect.height() / sample).coerceIn(1, whole.height - top),
        )
        if (part !== whole) whole.recycle()
        return part
    }

    private companion object {
        /** X, Y, U, V for each corner. */
        const val STRIDE_BYTES = 4 * 4

        /** What GLES 2.0 promises at least; a driver reporting less is not believed. */
        const val MIN_TEXTURE_SIZE = 2048

        const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            uniform vec2 uUvShift;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;

            void main() {
                gl_Position = aPosition;
                // The shift moves the window in the frame; the matrix takes the frame onto the decoded part.
                vTextureCoord = (uTexMatrix * (aTextureCoord + vec4(uUvShift, 0.0, 0.0))).xy;
            }
        """

        // A big texture needs more than mediump's ten bits to address its pixels.
        const val FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;

            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        fun quadOf(uv: UVCoords): FloatBuffer {
            val data = floatArrayOf(
                -1f, -1f, uv.bottomLeft.u, uv.bottomLeft.v,
                1f, -1f, uv.bottomRight.u, uv.bottomRight.v,
                -1f, 1f, uv.topLeft.u, uv.topLeft.v,
                1f, 1f, uv.topRight.u, uv.topRight.v,
            )
            return ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(data)
                position(0)
            }
        }
    }
}
