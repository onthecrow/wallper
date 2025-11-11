package com.onthecrow.wallper.service.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.view.Surface
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withSave
import androidx.media3.exoplayer.ExoPlayer
import com.onthecrow.wallper.R
import com.onthecrow.wallper.gl.OpenGLScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLES20WallpaperRenderer(
    private val context: Context,
    private val surfaceProvider: () -> GLSurfaceView,
    width: Int,
    height: Int,
) : GLWallpaperRenderer(width, height) {

    var openGLScene: OpenGLScene? = null

    override fun onRendererParamsChanged(params: RendererParams) {
        if (openGLScene == null) return
        openGLScene?.updateTextureParams(
            params.videoMetadata,
            params.rect,
            params.videoMetadata.rotation
        )
        setPlayerOrPlaceholder()
    }

    override fun setOffset(xOffset: Float, yOffset: Float) {}

    override fun dispose() {
        openGLScene?.release()
        openGLScene = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("onSurfaceCreated")
        openGLScene?.release()
        openGLScene = OpenGLScene(
            sceneHeight = rendererParams.height,
            sceneWidth = rendererParams.width,
            videoMetadata = rendererParams.videoMetadata,
            rect = rendererParams.rect
        )
        setPlayerOrPlaceholder()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Timber.d("Surface changed: $width x $height")
    }

    private var lastFpsLog = 0L
    private var frames = 0

    override fun onDrawFrame(gl: GL10?) {
        frames++
        val now = SystemClock.uptimeMillis()
        if (now - lastFpsLog > 1000) {
            Timber.d("GL draw fps=$frames/s")
            frames = 0; lastFpsLog = now
        }
        openGLScene?.updateFrame()
    }

    @Volatile private var onFrameAvailableListener: ((Surface) -> Unit)? = null

    fun doOnNextUpdateFrame(action: (Surface) -> Unit) {
        onFrameAvailableListener = action
    }

    private fun setPlayerOrPlaceholder() {
        Timber.d("Set player or placeholder with params: $rendererParams")
        openGLScene?.fullscreenTexture?.let { texture ->
            texture.createTexture()
            with(rendererParams) {
                when (this) {
                    is RendererParams.PictureParams -> draw(bitmap)
                    is RendererParams.PlaceholderParams -> drawPlaceholder()
                    is RendererParams.VideoParams -> runBlocking { attachPlayerSurfaceSafely(player, texture.surface) }
                }
            }
            texture.attachFrameListener(surfaceProvider())
            texture.doOnNextUpdateFrame {
                onFrameAvailableListener?.invoke(texture.surface)
                onFrameAvailableListener = null
            }
        }
        Timber.d("player set")
    }

    private var currentVideoSurface: Surface? = null

    private suspend fun attachPlayerSurfaceSafely(player: ExoPlayer, newSurface: Surface) {
        withContext(Dispatchers.Main.immediate) {
            val old = currentVideoSurface
//            player.setVideoSurface(newSurface)  // заменить атомарно
            Timber.d("Attach surface to player: ${System.identityHashCode(newSurface)}")
            // Теперь безопасно отпустить старый (если был)
            if (old != null && old != newSurface) {
                try { old.release() } catch (error: Throwable) {
                    Timber.e(error, "Failed to release old surface")
                }
                Timber.d("Old surface released")
            }
            currentVideoSurface = newSurface
        }
    }

    private fun drawPlaceholder() {
        Timber.d("Draw placeholder")
        val drawable = context.resources
            .getDrawable(R.drawable.bg_engine_empty, null)
            .toBitmap(width = rendererParams.width, height = rendererParams.height)
        draw(drawable)
    }

    private fun draw(bitmap: Bitmap) {
        Timber.d("Draw bitmap: $bitmap")
        openGLScene?.fullscreenTexture?.surface?.let { surface ->
            var canvas: Canvas? = null
            try {
                canvas = surface.lockHardwareCanvas()
                canvas?.withSave {
                    drawBitmap(bitmap, 0f, 0f, null)
                }
            } finally {
                if (canvas != null) surface.unlockCanvasAndPost(canvas)
            }
        }
    }
}