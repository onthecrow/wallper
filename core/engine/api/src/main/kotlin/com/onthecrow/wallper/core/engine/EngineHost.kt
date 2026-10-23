package com.onthecrow.wallper.core.engine

/** What a wallpaper engine may ask of the service. Callable from any thread. */
interface EngineHost {
    /**
     * Give up on the current wallpaper and show the placeholder until the user picks a wallpaper again.
     * [reason] and [error] go into the crash report.
     */
    fun showPlaceholder(reason: String, error: Throwable? = null)
}
