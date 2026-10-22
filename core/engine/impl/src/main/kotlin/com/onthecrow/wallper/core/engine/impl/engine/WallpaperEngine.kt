package com.onthecrow.wallper.core.engine.impl.engine

sealed class WallpaperEngine {
    abstract fun pause()
    abstract fun resume()
    abstract fun release()
    open fun onSurfaceChanged(width: Int, height: Int) {}
    open fun onSurfaceDestroyed() {}
}
