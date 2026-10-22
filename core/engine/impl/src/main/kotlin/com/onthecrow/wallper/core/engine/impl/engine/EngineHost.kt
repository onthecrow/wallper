package com.onthecrow.wallper.core.engine.impl.engine

/** What a wallpaper engine may ask of the service. Callable from any thread. */
internal interface EngineHost {
    /** Give up on the current wallpaper and show the placeholder until another one is selected. */
    fun showPlaceholder()
}
