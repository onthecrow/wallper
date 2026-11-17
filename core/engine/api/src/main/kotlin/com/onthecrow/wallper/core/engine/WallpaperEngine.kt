package com.onthecrow.wallper.core.engine

/**
 * What draws one wallpaper inside the service's `WallpaperService.Engine`. The service calls it on the
 * main thread; built-ins come from their features through [BuiltInWallpaperProvider].
 */
abstract class WallpaperEngine {
    abstract fun pause()
    abstract fun resume()
    abstract fun release()

    /**
     * For an engine made off screen ([SlideStart]): the window is free now, take it and put the picture
     * there, paused or not. The slide that had the window has let go of it.
     */
    open fun show() {}
    open fun onSurfaceChanged(width: Int, height: Int) {}
    open fun onSurfaceDestroyed() {}

    /** Launcher page offsets (`WallpaperService.Engine.onOffsetsChanged`); [xOffsetStep] 0 means no pages. */
    open fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {}

    /** `WallpaperService.Engine.onZoomChanged` (API 30+): 0 fully zoomed in, 1 fully zoomed out. */
    open fun onZoomChanged(zoom: Float) {}
}
