package com.onthecrow.wallper.core.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageScrollTest {

    @Test
    fun `a crop in the middle shows as chosen on the middle page and pans both ways`() {
        assertEquals(0f, PageScroll.shiftU(0.375f, 0.625f, pageOffset = 0.5f), DELTA)
        assertEquals(-0.0625f, PageScroll.shiftU(0.375f, 0.625f, pageOffset = 0f), DELTA)
        assertEquals(0.0625f, PageScroll.shiftU(0.375f, 0.625f, pageOffset = 1f), DELTA)
    }

    @Test
    fun `a crop at the left edge shows as chosen on the first page`() {
        assertEquals(0f, PageScroll.shiftU(0f, 0.25f, pageOffset = 0f), DELTA)
        assertEquals(0.125f, PageScroll.shiftU(0f, 0.25f, pageOffset = 1f), DELTA)
    }

    @Test
    fun `a crop at the right edge shows as chosen on the last page`() {
        assertEquals(-0.125f, PageScroll.shiftU(0.75f, 1f, pageOffset = 0f), DELTA)
        assertEquals(0f, PageScroll.shiftU(0.75f, 1f, pageOffset = 1f), DELTA)
    }

    @Test
    fun `a crop as wide as the frame doesn't move`() {
        assertEquals(0f, PageScroll.shiftU(0f, 1f, pageOffset = 0f), DELTA)
        assertEquals(0f, PageScroll.shiftU(0f, 1f, pageOffset = 1f), DELTA)
    }

    @Test
    fun `the window never leaves the frame`() {
        // Only 0.05 of the frame is left beside a crop 0.8 wide.
        listOf(0f, 0.5f, 1f).forEach { offset ->
            val shift = PageScroll.shiftU(0.1f, 0.9f, offset)
            assertTrue(0.1f + shift >= -DELTA && 0.9f + shift <= 1f + DELTA, "out of the frame at $offset: $shift")
        }
    }

    private companion object {
        const val DELTA = 1e-6f
    }
}
