package com.onthecrow.wallper.service.renderer

import android.opengl.GLSurfaceView
import kotlin.properties.Delegates

abstract class GLWallpaperRenderer(width: Int, height: Int) : GLSurfaceView.Renderer {

    var rendererParams by Delegates.observable<RendererParams>(RendererParams.PlaceholderParams(width, height)) { _, _, newValue ->
        onRendererParamsChanged(newValue)
    }

    protected abstract fun onRendererParamsChanged(params: RendererParams)
    abstract fun setOffset(xOffset: Float, yOffset: Float)
    abstract fun dispose()
}