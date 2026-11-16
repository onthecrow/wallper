package com.onthecrow.wallper.core.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.model.SeriesTimeline
import com.onthecrow.wallper.core.model.SlideTransition

/**
 * A series played inside the app on a screen's surface, which can stand still on any moment of its round
 * ([timeline]) and be moved to another: each slide at its own time there, a transition at its progress. It is
 * drawn with the wallpaper's own renderers and transitions, and played by players of its own: nothing of the
 * wallpaper's engines, schedule or players. Main thread only.
 */
interface SeriesPreview {
    val timeline: SeriesTimeline

    /** Where it is in its round, in ms, from 0 to the round's duration. Moves on while it plays. */
    val positionMs: Long

    val isPlaying: Boolean

    fun play()

    /** Stands still on the moment it is at. */
    fun pause()

    /** Shows the moment at [positionMs]; it plays on from there if it plays. */
    fun seekTo(positionMs: Long)

    /** Many seeks are coming, a finger on the timeline: its players are tuned for them meanwhile. */
    fun setScrubbing(scrubbing: Boolean)

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
