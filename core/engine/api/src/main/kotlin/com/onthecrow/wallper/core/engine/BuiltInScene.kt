package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.gl.GLWallpaperRenderer

/**
 * A built-in wallpaper at any moment of its own time, for a preview that moves back and forth in it: drawn
 * by [renderer] on whatever GL thread runs it, as it is at the last moment [showAt] was given.
 */
interface BuiltInScene {
    /** GL thread only, like any renderer: its GL thread sets it up and disposes of it. */
    val renderer: GLWallpaperRenderer

    /** Main thread. The next draws show the wallpaper [timeNanos] into its own time. */
    fun showAt(timeNanos: Long)

    /** Lets go of what it holds besides the renderer. */
    fun release()
}
