package com.onthecrow.wallper.core.model

/**
 * How a series blends one slide into the next: [effect] over [durationMs]. Only a series whose slides share
 * one GL thread can blend two of them (`SeriesSwitch.SHARED_GL`); any other series cuts.
 */
data class SlideTransition(
    val effect: TransitionEffect = TransitionEffect.NONE,
    val durationMs: Long = DEFAULT_DURATION_MS,
) {
    val isNone: Boolean get() = effect == TransitionEffect.NONE

    companion object {
        val NONE = SlideTransition()

        const val DEFAULT_DURATION_MS = 1_000L
        const val MIN_DURATION_MS = 400L
        const val MAX_DURATION_MS = 2_500L
        const val DURATION_STEP_MS = 100L

        /** [durationMs] as one the user can pick: in range, on a step. */
        fun duration(durationMs: Long): Long {
            val inRange = durationMs.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
            return MIN_DURATION_MS + (inRange - MIN_DURATION_MS + DURATION_STEP_MS / 2) / DURATION_STEP_MS * DURATION_STEP_MS
        }
    }
}

/**
 * What a transition looks like; the order is the one the user picks from. Stored by [key]: never change one.
 */
enum class TransitionEffect(val key: String) {
    /** The next slide simply takes the screen. */
    NONE("none"),

    /** One picture dissolves into the other, mixed in linear light. */
    CROSSFADE("crossfade"),

    /** Down to black, and up from it to the next. */
    DIP_TO_BLACK("dip_to_black"),

    /** Both blur, the blur dissolving from one into the other. */
    BLUR("blur"),

    /** The picture grows towards the viewer as the next one settles in. */
    ZOOM("zoom"),

    /** The next picture pushes the one on screen up and out. */
    SLIDE_UP("slide_up"),

    /** The next picture shows its brightest parts first. */
    LUMA("luma"),

    /** A soft edge sweeps across, from the bottom left corner. */
    WIPE("wipe"),

    /** A circle opens from the middle. */
    IRIS("iris"),

    /** Ripples run out from the middle while one picture dissolves into the other. */
    RIPPLE("ripple"),

    /** Both break up into squares, which grow and shrink again. */
    PIXELATE("pixelate");

    companion object {
        /** [NONE] for an unknown key: a transition of a later version. */
        fun fromKey(key: String?): TransitionEffect = entries.firstOrNull { it.key == key } ?: NONE
    }
}
