package com.onthecrow.wallper.core.engine.impl.engine

sealed class WallpaperEngine() {
    abstract fun pause()
    abstract fun resume()
    abstract fun release()
}