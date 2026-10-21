package com.onthecrow.wallper.core.gl

import android.opengl.GLSurfaceView

abstract class GLWallpaperRenderer : GLSurfaceView.Renderer {
    abstract fun setOffset(xOffset: Float, yOffset: Float)
    abstract fun dispose()
}