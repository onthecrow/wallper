package com.onthecrow.wallper.editor

import kotlin.math.roundToInt

/**
 * Where the editor's parts are while the tint's picker takes the place of the trim bar and the save row:
 * [progress] of the way there, 0 with the trim bar and the save row under the buttons, 1 with the picker. The
 * way back is the same one, backwards.
 *
 * The room under the buttons grows from the height of the one to the height of the other, and the crop and the
 * buttons above it keep what is left. The trim bar and the save row sink towards the bottom edge and fade out
 * over the first half of the way; the picker rises from under that edge the whole way, fading in as it comes,
 * and travels the last third plain to see. One is all but gone before the other shows much, so they never
 * read as two things on top of each other.
 *
 * All in pixels of a layout [height] high, measured from its top.
 */
internal class TintPanelMotion(
    height: Int,
    controlsHeight: Int,
    bottomHeight: Int,
    tintHeight: Int,
    progress: Float,
) {
    private val fraction = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

    /** How much of the height the crop has, from the top down to the buttons. */
    val cropHeight: Int = roomOver(height, controlsHeight, lerp(bottomHeight, tintHeight, fraction))

    /** The buttons stay right under the crop. */
    val controlsTop: Int get() = cropHeight

    /** The trim bar and the save row: under the buttons at first, then down by as much as they are high. */
    val bottomTop: Int =
        fullCropHeight(height, controlsHeight, bottomHeight) + controlsHeight + (bottomHeight * fraction).roundToInt()

    /** 0 from [BOTTOM_GONE] on: nothing of them is left to show, or to touch. */
    val bottomAlpha: Float = 1f - (fraction / BOTTOM_GONE).coerceIn(0f, 1f)

    /** The picker: from just under the bottom edge to right under the buttons. */
    val tintTop: Int = lerp(height, roomOver(height, controlsHeight, tintHeight) + controlsHeight, fraction)

    /** 0 up to [TINT_FROM], 1 from [TINT_FULL] on. */
    val tintAlpha: Float = ((fraction - TINT_FROM) / (TINT_FULL - TINT_FROM)).coerceIn(0f, 1f)

    companion object {
        /** How long the whole way takes. */
        const val DURATION_MS = 400

        /** How far along the way the trim bar and the save row are gone. */
        const val BOTTOM_GONE = 0.5f

        /** How far along the way the picker starts to show, and where it is plain to see. */
        const val TINT_FROM = 0.15f
        const val TINT_FULL = 0.65f

        /**
         * The room the crop is laid out for: all that the buttons, the trim bar and the save row leave it. What
         * [cropHeight] is at the start of the way; less of it is seen as the picker comes.
         */
        fun fullCropHeight(height: Int, controlsHeight: Int, bottomHeight: Int): Int =
            roomOver(height, controlsHeight, bottomHeight)

        /** What is left of [height] above the buttons and whatever is [underHeight] high under them. */
        private fun roomOver(height: Int, controlsHeight: Int, underHeight: Int): Int =
            (height - controlsHeight - underHeight).coerceAtLeast(0)

        private fun lerp(start: Int, stop: Int, fraction: Float): Int =
            start + ((stop - start) * fraction).roundToInt()
    }
}
