package com.onthecrow.wallper.service.renderer

import android.opengl.GLSurfaceView
import kotlin.properties.Delegates

abstract class GLWallpaperRenderer : GLSurfaceView.Renderer {
    protected abstract fun onRendererParamsChanged(params: RendererParams)
    abstract fun setOffset(xOffset: Float, yOffset: Float)
    abstract fun dispose()
}