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
        openGLScene?.updateTextureParams(params.videoMetadata, params.rect)
        setPlayerOrPlaceholder()
    }

    override fun setOffset(xOffset: Float, yOffset: Float) {  }

    override fun dispose() {
        openGLScene?.release()
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
        openGLScene?.fullscreenTexture?.let { texture ->
            texture.createTexture()
            MainScope().launch(Dispatchers.Main) {
                rendererParams.player?.setVideoSurface(texture.surface) ?: drawPlaceholder()
            }
        }
    }

    private fun drawPlaceholder() {
        val drawable = context.resources
            .getDrawable(R.drawable.bg_engine_empty, null)
            .toBitmap(width = rendererParams.width, height = rendererParams.height)
        draw(drawable)
    }

    private fun draw(drawable: Bitmap) {
        openGLScene?.fullscreenTexture?.surface?.let { surface ->
            var canvas: Canvas? = null
            try {
                canvas = surface.lockHardwareCanvas()
                if (canvas != null) {
                    canvas.save()
                    canvas.drawBitmap(drawable, 0f, 0f, null)
                    canvas.restore()
                }
            } finally {
                if (canvas != null) surface.unlockCanvasAndPost(canvas)
            }
        }
    }
}