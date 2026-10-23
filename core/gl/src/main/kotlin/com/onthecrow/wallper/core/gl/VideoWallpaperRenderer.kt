package com.onthecrow.wallper.core.gl

import android.opengl.GLES20
import android.os.Handler
import android.view.Surface
import timber.log.Timber

/**
 * Renders video frames cropped through UV coordinates.
 *
 * Owns one video texture per EGL context and hands its surface out through [Listener.onVideoSurfaceCreated].
 * The renderer knows nothing about players.
 *
 * GL methods run on the GL thread; listener calls arrive on [callbackHandler].
 */
class VideoWallpaperRenderer(
    private val renderParams: VideoRenderParams,
    private val callbackHandler: Handler,
    private val listener: Listener,
) : GLWallpaperRenderer() {

    interface Listener {
        /** The texture got a new frame: request a render. */
        fun onFrameAvailable()

        /** A new texture is ready, also after the EGL context was recreated: render into [surface] from now on. */
        fun onVideoSurfaceCreated(surface: Surface)
    }

    private var scene: OpenGLScene? = null
    private var texture: OpenGLExternalTexture? = null

    /** The texture of a lost context. The player may still decode into it; released on the first new frame. */
    private var retired: OpenGLExternalTexture? = null

    override fun setOffset(xOffset: Float, yOffset: Float) {}

    override fun onSurfaceCreated() {
        Timber.d("onSurfaceCreated")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glDisable(GLES20.GL_BLEND) // nothing here is translucent
        GLES20.glDisable(GLES20.GL_DITHER)
        val newScene = OpenGLScene(
            sceneHeight = renderParams.height,
            sceneWidth = renderParams.width,
            mediaInfo = renderParams.mediaInfo,
            rect = renderParams.crop,
        )
        val newTexture = newScene.createTexture().apply {
            setFrameListener(callbackHandler, listener::onFrameAvailable)
        }
        scene = newScene
        texture = newTexture
        callbackHandler.post { listener.onVideoSurfaceCreated(newTexture.surface) }
    }

    override fun onSurfaceChanged(width: Int, height: Int) {
        Timber.d("Surface changed: $width x $height")
    }

    override fun onDrawFrame(): Boolean {
        val currentScene = scene ?: return false
        val currentTexture = texture ?: return false
        // Latching on every draw re-arms a SurfaceTexture that stopped sending frame callbacks.
        val isNewFrame = currentTexture.latch()
        if (isNewFrame) releaseRetired()
        currentScene.draw(currentTexture)
        return isNewFrame
    }

    override fun onContextLost() {
        retired?.release(deleteTexture = false)
        retired = texture
        texture = null
        scene?.release(deleteProgram = false)
        scene = null
    }

    override fun dispose() {
        Timber.d("dispose()")
        releaseRetired()
        texture?.release()
        texture = null
        scene?.release()
        scene = null
    }

    private fun releaseRetired() {
        retired?.release(deleteTexture = false)
        retired = null
    }
}
