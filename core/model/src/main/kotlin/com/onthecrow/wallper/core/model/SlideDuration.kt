package com.onthecrow.wallper.core.model

/**
 * How long each wallpaper of a series stays on screen, in [ms] of the time it is visible; or, with
 * [ON_WAKE], until the screen turns off: the next one is there when it turns on again.
 */
@JvmInline
value class SlideDuration(val ms: Long) {
    val isOnWake: Boolean get() = ms == ON_WAKE.ms

    companion object {
        val ON_WAKE = SlideDuration(0)
        val DEFAULT = SlideDuration(60_000)

        /** What the user picks from, shortest first: switching wallpapers costs, so none is shorter. */
        val CHOICES = listOf(15_000L, 30_000L, 60_000L, 300_000L, 900_000L, 3_600_000L).map(::SlideDuration) + ON_WAKE
    }
}
