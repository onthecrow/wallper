package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.view.Surface
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renders a video frame cropped through UV coordinates.
 *
 * @param onFrameAvailable called when the decoder posts a new frame; request a render here.
 * @param onVideoSurfaceCreated receives the [Surface] the player must decode into. Called on the main thread.
 */
class VideoWallpaperRenderer(
    private val renderParams: VideoRenderParams,
    private val onFrameAvailable: () -> Unit,
    private val onVideoSurfaceCreated: (Surface) -> Unit,
) : GLWallpaperRenderer() {

    private var openGLScene: OpenGLScene? = null

    override fun setOffset(xOffset: Float, yOffset: Float) {}

    override fun dispose() {
        Timber.d("dispose()")
        openGLScene?.release()
        openGLScene = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("onSurfaceCreated")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)    // если не рисуете полупрозрачность
        GLES20.glDisable(GLES20.GL_DITHER)
        val scene = OpenGLScene(
            sceneHeight = renderParams.height,
            sceneWidth = renderParams.width,
            mediaInfo = renderParams.mediaInfo,
            rect = renderParams.crop,
        )
        openGLScene = scene
        scene.fullscreenTexture.attachFrameListener { onFrameAvailable() }
        Handler(Looper.getMainLooper()).post {
            onVideoSurfaceCreated(scene.fullscreenTexture.surface)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("Surface changed: $width x $height")
    }

    override fun onDrawFrame(gl: GL10?) {
        openGLScene?.updateFrame()
    }
}
