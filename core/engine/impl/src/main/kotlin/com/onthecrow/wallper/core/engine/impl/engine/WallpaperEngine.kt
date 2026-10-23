package com.onthecrow.wallper.core.engine.impl.engine

internal sealed class WallpaperEngine {
    abstract fun pause()
    abstract fun resume()
    abstract fun release()
    open fun onSurfaceChanged(width: Int, height: Int) {}
    open fun onSurfaceDestroyed() {}

    /** Launcher page offsets (`WallpaperService.Engine.onOffsetsChanged`); [xOffsetStep] 0 means no pages. */
    open fun onOffsetsChanged(xOffset: Float, xOffsetStep: Float) {}

    /** `WallpaperService.Engine.onZoomChanged` (API 30+): 0 fully zoomed in, 1 fully zoomed out. */
    open fun onZoomChanged(zoom: Float) {}
}
