package com.onthecrow.wallper.core.gl

/** Draws the wallpaper on [WallpaperGLThread]. Every method runs on the GL thread with the EGL context current. */
abstract class GLWallpaperRenderer {

    /** A new EGL context is current: create GL objects. Called again after the context was recreated. */
    abstract fun onSurfaceCreated()

    abstract fun onSurfaceChanged(width: Int, height: Int)

    /** Draws one frame. Returns true if it put a new video frame on screen (the render guard's progress signal). */
    abstract fun onDrawFrame(): Boolean

    /** The context is about to be destroyed and recreated; GL objects die with it. */
    open fun onContextLost() = dispose()

    abstract fun dispose()
}
