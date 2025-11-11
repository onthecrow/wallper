package com.onthecrow.wallper.service.renderer

import android.opengl.GLSurfaceView
import timber.log.Timber
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.properties.Delegates

abstract class GLWallpaperRenderer(width: Int, height: Int) : GLSurfaceView.Renderer {

    var rendererParams by Delegates.observable<RendererParams>(RendererParams.PlaceholderParams(width, height)) { _, _, newValue ->
        Timber.d("Renderer set params: $newValue")
        onRendererParamsChanged(newValue)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Timber.d("onSurfaceCreated: $gl, $config")
    }

    protected abstract fun onRendererParamsChanged(params: RendererParams)
    abstract fun setOffset(xOffset: Float, yOffset: Float)
    abstract fun dispose()
}