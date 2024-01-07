package com.onthecrow.wallper.service.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.drawable.toBitmap
import com.onthecrow.wallper.R
import com.onthecrow.wallper.gl.OpenGLScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLES20WallpaperRenderer(
    private val context: Context,
    width: Int,
    height: Int,
) : GLWallpaperRenderer(width, height) {

    private var openGLScene: OpenGLScene? = null

    override fun onRendererParamsChanged(params: RendererParams) {
        if (openGLScene == null) return
        openGLScene?.updateTextureParams(params.videoMetadata, params.rect, params.videoMetadata.rotation)
        setPlayerOrPlaceholder()
    }

    override fun setOffset(xOffset: Float, yOffset: Float) {  }

    override fun dispose() {
        openGLScene?.release()
        openGLScene = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        openGLScene = OpenGLScene(
            sceneHeight = rendererParams.height,
            sceneWidth = rendererParams.width,
            videoMetadata = rendererParams.videoMetadata,
            rect = rendererParams.rect
        )
        setPlayerOrPlaceholder()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {}

    override fun onDrawFrame(gl: GL10?) {
        openGLScene?.updateFrame()
    }

    private fun setPlayerOrPlaceholder() {
        Timber.d("Set player or placeholder with params: $rendererParams")
        openGLScene?.fullscreenTexture?.let { texture ->
            texture.createTexture()
            with(rendererParams) {
                when (this) {
                    is RendererParams.PictureParams -> draw(bitmap)
                    is RendererParams.PlaceholderParams -> drawPlaceholder()
                    is RendererParams.VideoParams -> MainScope().launch(Dispatchers.Main) {
                        player.setVideoSurface(texture.surface)
                    }
                }
            }
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
                if (canvas != null) {
                    canvas.save()
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    canvas.restore()
                }
            } finally {
                if (canvas != null) surface.unlockCanvasAndPost(canvas)
            }
        }
    }
}