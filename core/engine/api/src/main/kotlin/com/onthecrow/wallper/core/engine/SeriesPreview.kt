package com.onthecrow.wallper.core.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.model.SeriesTimeline
import com.onthecrow.wallper.core.model.SlideTransition

/**
 * A series played inside the app on a screen's surface, round and round: each slide alone for a while, then
 * the transition into the next, the last one's into the first ([SeriesTimeline]). It is drawn with the
 * wallpaper's own renderers and transitions, and played by players of its own: nothing of the wallpaper's
 * engines, schedule or players. Main thread only.
 */
interface SeriesPreview {
    fun play()

    /** Stands still on the moment it is at. */
    fun pause()

    /** Lets go of everything it made, the surface first: the surface is free when this returns. */
    fun release()
}

/** Makes [SeriesPreview]s. */
interface SeriesPreviews {
    /**
     * A preview of [slides] (at least one) in turn, each alone for [holdMs] and coming in through
     * [transition], drawn on [surfaceHolder] at its size then. It stands still at the start until [SeriesPreview.play].
     */
    fun create(
        context: Context,
        surfaceHolder: SurfaceHolder,
        slides: List<WallpaperSpec>,
        transition: SlideTransition,
        holdMs: Long,
    ): SeriesPreview
}
