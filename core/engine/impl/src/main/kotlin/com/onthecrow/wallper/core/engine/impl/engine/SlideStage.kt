package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.WallpaperEngine
import kotlin.time.Duration

/**
 * How a series gets its slides onto the wallpaper window: [SeriesWallpaperEngine] decides when, this decides
 * how. It is the engine of whatever slide is on screen, so the service's calls go through it.
 *
 * Called on the main thread, like every engine.
 */
internal abstract class SlideStage : WallpaperEngine() {

    /** How long before a slide's time is up the next one starts to get ready; zero when nothing is prepared. */
    abstract val lead: Duration

    /**
     * Gets [slide] ready off screen, beside the one showing. Returns once it is ready, or once it is clear
     * that it won't be in time; cancelling it lets the prepared slide go.
     */
    abstract suspend fun prepare(slide: Int)

    /** Puts [slide] on screen instead of the current one, prepared or not. */
    abstract fun switchTo(slide: Int)

    /** Lets go of a slide that was prepared and not shown. */
    abstract fun dropPrepared()
}
