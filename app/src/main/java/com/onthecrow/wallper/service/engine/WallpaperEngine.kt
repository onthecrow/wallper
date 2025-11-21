package com.onthecrow.wallper.service.engine

sealed class WallpaperEngine() {
    abstract fun pause()
    abstract fun resume()
    abstract fun release()
}