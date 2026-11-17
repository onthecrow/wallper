package com.onthecrow.wallper.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TintPanelMotionTest {

    private fun motion(progress: Float) = TintPanelMotion(
        height = HEIGHT,
        controlsHeight = CONTROLS,
        bottomHeight = BOTTOM,
        tintHeight = TINT,
        progress = progress,
    )

    /** Every twentieth of the way, from its start to its end. */
    private val steps = (0..20).map { it / 20f }

    @Test
    fun `at the start the trim bar and the save row are under the buttons and the picker is out of sight`() {
        val start = motion(0f)

        assertEquals(HEIGHT - CONTROLS - BOTTOM, start.cropHeight)
        assertEquals(TintPanelMotion.fullCropHeight(HEIGHT, CONTROLS, BOTTOM), start.cropHeight)
        assertEquals(HEIGHT - CONTROLS - BOTTOM, start.controlsTop)
        assertEquals(HEIGHT - BOTTOM, start.bottomTop)
        assertEquals(1f, start.bottomAlpha)
        // Just under the bottom edge, and not shown.
        assertEquals(HEIGHT, start.tintTop)
        assertEquals(0f, start.tintAlpha)
    }

    @Test
    fun `at the end the picker is under the buttons and nothing is left of the trim bar and the save row`() {
        val end = motion(1f)

        assertEquals(HEIGHT - CONTROLS - TINT, end.cropHeight)
        assertEquals(HEIGHT - CONTROLS - TINT, end.controlsTop)
        assertEquals(HEIGHT - TINT, end.tintTop)
        assertEquals(1f, end.tintAlpha)
        assertEquals(0f, end.bottomAlpha)
        // They went down by their whole height: under the bottom edge.
        assertEquals(HEIGHT, end.bottomTop)
    }

    @Test
    fun `the picker rises and shows while the trim bar and the save row sink and fade`() {
        steps.zipWithNext().forEach { (before, after) ->
            val from = motion(before)
            val to = motion(after)

            assertTrue(to.tintTop < from.tintTop, "the picker rises from $before to $after")
            assertTrue(to.tintAlpha >= from.tintAlpha, "the picker shows from $before to $after")
            assertTrue(to.bottomTop > from.bottomTop, "the trim bar sinks from $before to $after")
            assertTrue(to.bottomAlpha <= from.bottomAlpha, "the trim bar fades from $before to $after")
            assertTrue(to.cropHeight < from.cropHeight, "the crop gives way from $before to $after")
        }
    }

    @Test
    fun `the buttons stay between the crop and whatever is under them`() {
        steps.forEach { progress ->
            val at = motion(progress)

            assertEquals(at.cropHeight, at.controlsTop)
            assertTrue(at.controlsTop + CONTROLS <= at.bottomTop, "over the trim bar at $progress")
            assertTrue(at.controlsTop + CONTROLS <= at.tintTop, "over the picker at $progress")
        }
    }

    @Test
    fun `one is nearly gone before the other shows much`() {
        steps.forEach { progress ->
            val at = motion(progress)

            assertTrue(at.bottomAlpha + at.tintAlpha <= 1f, "at $progress")
        }
        // And for a while both are there, so that nothing blinks between them.
        val between = motion((TintPanelMotion.TINT_FROM + TintPanelMotion.BOTTOM_GONE) / 2)
        assertTrue(between.bottomAlpha > 0f && between.tintAlpha > 0f)
    }

    @Test
    fun `the trim bar and the save row are gone half way and the picker is plain to see for the last third`() {
        assertTrue(motion(TintPanelMotion.BOTTOM_GONE - 0.01f).bottomAlpha > 0f)
        assertEquals(0f, motion(TintPanelMotion.BOTTOM_GONE).bottomAlpha)
        assertEquals(0f, motion(TintPanelMotion.TINT_FROM).tintAlpha)
        assertEquals(1f, motion(TintPanelMotion.TINT_FULL).tintAlpha, 1e-6f)
        // Still on its way up when it is.
        assertTrue(motion(TintPanelMotion.TINT_FULL).tintTop > motion(1f).tintTop)
    }

    @Test
    fun `progress out of range, or none, is the nearest end`() {
        listOf(-0.5f, Float.NEGATIVE_INFINITY, Float.NaN).forEach { progress ->
            assertEquals(motion(0f).tintTop, motion(progress).tintTop)
            assertEquals(1f, motion(progress).bottomAlpha)
            assertEquals(0f, motion(progress).tintAlpha)
        }
        listOf(1.5f, Float.POSITIVE_INFINITY).forEach { progress ->
            assertEquals(motion(1f).tintTop, motion(progress).tintTop)
            assertEquals(0f, motion(progress).bottomAlpha)
            assertEquals(1f, motion(progress).tintAlpha)
        }
    }

    @Test
    fun `a picker as high as what it replaces leaves the crop and the buttons where they are`() {
        steps.forEach { progress ->
            val at = TintPanelMotion(HEIGHT, CONTROLS, bottomHeight = BOTTOM, tintHeight = BOTTOM, progress = progress)

            assertEquals(HEIGHT - CONTROLS - BOTTOM, at.cropHeight)
            assertEquals(HEIGHT - CONTROLS - BOTTOM, at.controlsTop)
        }
    }

    @Test
    fun `too little height leaves the crop none and keeps the picker under the buttons`() {
        val height = CONTROLS + TINT - 100

        val end = TintPanelMotion(height, CONTROLS, BOTTOM, TINT, progress = 1f)

        assertEquals(0, end.cropHeight)
        assertEquals(0, end.controlsTop)
        // Its end is cut off by the bottom edge rather than its top by the buttons.
        assertEquals(CONTROLS, end.tintTop)
        assertEquals(0, TintPanelMotion.fullCropHeight(height = 100, controlsHeight = CONTROLS, bottomHeight = BOTTOM))
    }

    private companion object {
        const val HEIGHT = 2000
        const val CONTROLS = 150
        const val BOTTOM = 400
        const val TINT = 800
    }
}
