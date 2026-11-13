package com.onthecrow.wallper.core.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeRevealMathTest {

    @Test
    fun `from the middle the circle reaches every corner at once`() {
        assertEquals(50f, farthestCornerDistance(x = 30f, y = 40f, width = 60f, height = 80f), DELTA)
    }

    @Test
    fun `from a corner the circle has to reach the opposite one`() {
        assertEquals(100f, farthestCornerDistance(x = 0f, y = 0f, width = 60f, height = 80f), DELTA)
        assertEquals(100f, farthestCornerDistance(x = 60f, y = 80f, width = 60f, height = 80f), DELTA)
    }

    @Test
    fun `off the middle the circle reaches the farther side on each axis`() {
        // A tap in a dialog low on the left: the top right corner is the last to be uncovered.
        assertEquals(50f, farthestCornerDistance(x = 10f, y = 40f, width = 40f, height = 50f), DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
