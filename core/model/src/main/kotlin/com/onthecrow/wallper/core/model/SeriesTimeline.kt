package com.onthecrow.wallper.core.model

/**
 * One round of a series as a preview plays it: each of [slideCount] slides shows alone for [holdMs], then the
 * transition into the next one takes [transitionMs], the last one's into the first. A series of one has no
 * transition: its slide simply goes round.
 *
 * A slide's own time starts where the transition that brings it in starts, and runs on through its hold and
 * the transition that takes it away: as on the wallpaper, where the next slide starts playing as it comes in
 * and the one before plays on under it. So the first slide is [transitionMs] into its time at position 0.
 */
data class SeriesTimeline(val slideCount: Int, val holdMs: Long, val transitionMs: Long) {

    init {
        require(slideCount > 0) { "A timeline needs slides" }
        require(holdMs > 0) { "A slide shows for some time" }
        require(transitionMs >= 0) { "A transition takes no negative time" }
    }

    /** What the transitions really take: nothing with one slide, which has none to blend into. */
    val blendMs: Long = if (slideCount > 1) transitionMs else 0

    /** A slide alone and the transition after it. */
    val slotMs: Long = holdMs + blendMs

    /** The whole round. */
    val durationMs: Long = slideCount * slotMs

    /** Where slide [index] starts to show alone. */
    fun holdStartOf(index: Int): Long = index * slotMs

    /** Where the transition from slide [index] into the next one starts. */
    fun transitionStartOf(index: Int): Long = index * slotMs + holdMs

    /** What shows at [positionMs], any number of rounds away (also before the start): it goes round. */
    fun at(positionMs: Long): Moment {
        val position = Math.floorMod(positionMs, durationMs)
        val index = (position / slotMs).toInt()
        val withinSlot = position - index * slotMs
        if (withinSlot < holdMs) return Moment.Slide(index, sinceShownMs = blendMs + withinSlot)
        val intoBlend = withinSlot - holdMs
        return Moment.Transition(
            from = index,
            to = (index + 1) % slideCount,
            progress = intoBlend.toFloat() / blendMs,
            fromSinceShownMs = blendMs + withinSlot,
            toSinceShownMs = intoBlend,
        )
    }

    /** What a preview shows at a position, with each slide's own time there. */
    sealed interface Moment {
        /** Every slide the moment draws, with its own time. */
        val slides: Map<Int, Long>

        /** Slide [index] alone, [sinceShownMs] into its own time. */
        data class Slide(val index: Int, val sinceShownMs: Long) : Moment {
            override val slides: Map<Int, Long> get() = mapOf(index to sinceShownMs)
        }

        /** Slide [from] blending into [to], [progress] (0 to 1) of the way. */
        data class Transition(
            val from: Int,
            val to: Int,
            val progress: Float,
            val fromSinceShownMs: Long,
            val toSinceShownMs: Long,
        ) : Moment {
            override val slides: Map<Int, Long> get() = mapOf(from to fromSinceShownMs, to to toSinceShownMs)
        }
    }
}
