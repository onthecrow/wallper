package com.onthecrow.wallper.service.renderer

import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import com.onthecrow.wallper.gl.OpenGLScene
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLES20WallpaperRenderer(
    private var onFrameAvailable: () -> Unit,
    private val renderParams: RendererParams.VideoParams,
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
        openGLScene = OpenGLScene(
            sceneHeight = renderParams.height,
            sceneWidth = renderParams.width,
            videoMetadata = renderParams.videoMetadata,
            rect = renderParams.rect
        )
        openGLScene!!.fullscreenTexture.attachFrameListener { onFrameAvailable() }
        Handler(Looper.getMainLooper()).post {
            renderParams.player.setVideoSurface(openGLScene!!.fullscreenTexture.surface)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("Surface changed: $width x $height")
    }

    override fun onDrawFrame(gl: GL10?) {
        openGLScene?.updateFrame()
    }

//    private fun setPlayerOrPlaceholder() {
//        openGLScene?.fullscreenTexture?.let { texture ->
//            texture.createTexture()
//            with(renderParams) {
//                when (this) {
//                    is RendererParams.VideoParams -> runBlocking {
//                        attachPlayerSurfaceSafely(player, texture.surface)
//                    }
//                }
//            }
//            // при непрерывном рендере слушатель кадров не нужен
//            texture.attachFrameListener {
//                onFrameAvailable()  // лямбда, переданная из движка
//            }
//        }
//    }

//    private var currentVideoSurface: Surface? = null
//
//    private suspend fun attachPlayerSurfaceSafely(player: ExoPlayer, newSurface: Surface) {
//        withContext(Dispatchers.Main.immediate) {
//            val old = currentVideoSurface
//            Timber.d("Attaching player surface ${System.identityHashCode(newSurface)}...")
//            player.setVideoSurface(newSurface)  // заменить атомарно
//            Timber.d("Player surface attached ${System.identityHashCode(newSurface)}...")
//            // Теперь безопасно отпустить старый (если был)
//            if (old != null && old != newSurface) {
//                try {
//                    old.release()
//                } catch (_: Throwable) {
//                }
//            }
//            currentVideoSurface = newSurface
//        }
//    }
}
