package com.onthecrow.wallper.service.renderer

import android.opengl.GLSurfaceView

abstract class GLWallpaperRenderer : GLSurfaceView.Renderer {
    abstract fun setOffset(xOffset: Float, yOffset: Float)
    abstract fun dispose()
}