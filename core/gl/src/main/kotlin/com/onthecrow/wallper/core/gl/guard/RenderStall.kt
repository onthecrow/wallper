package com.onthecrow.wallper.core.gl.guard

/** Why the screen stopped updating. */
sealed interface RenderStall {
    /** No new frame was put on screen for a while. Either a lost frame callback or nothing to show. */
    data object NoFrames : RenderStall

    /** eglSwapBuffers failed with EGL_BAD_SURFACE or EGL_BAD_NATIVE_WINDOW. */
    data object SurfaceLost : RenderStall

    /** eglSwapBuffers failed otherwise, typically EGL_CONTEXT_LOST after the device woke up. */
    data object ContextLost : RenderStall

    /** Drawing threw, e.g. SurfaceTexture.updateTexImage. */
    data object DrawFailed : RenderStall
}

/** Repairs only the GL thread can do. All of them run on the GL thread. */
interface RenderRepairs {
    /** Draw again; latching re-arms a SurfaceTexture that stopped sending frame callbacks. */
    fun redraw()

    fun recreateSurface()

    /** Recreate the EGL context and every GL object; the video slots get new surfaces. */
    fun recreateContext()
}
