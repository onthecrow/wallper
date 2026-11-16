package com.onthecrow.wallper.wallpapers.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class ScrubBarMathTest {

    /** A 1000 px bar with 50 px insets: the round runs over the 900 px between them. */
    private fun at(x: Float) = ScrubBarMath.positionAt(x, inset = 50f, width = 1_000f, durationMs = 18_000)

    @Test
    fun `the round runs between the insets`() {
        assertEquals(0, at(50f))
        assertEquals(9_000, at(500f))
        assertEquals(4_500, at(275f))
    }

    @Test
    fun `past an end is that end, and the end is never the start again`() {
        assertEquals(0, at(0f))
        assertEquals(17_999, at(950f))
        assertEquals(17_999, at(2_000f))
    }

    @Test
    fun `a bar with no room has nothing to point at`() {
        assertEquals(0, ScrubBarMath.positionAt(10f, inset = 50f, width = 80f, durationMs = 18_000))
    }
}
