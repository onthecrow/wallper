package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageScrollTest {

    @Test
    fun `a crop in the middle shows as chosen on the middle page and pans both ways`() {
        assertEquals(0f, shiftU(375, 625, pageOffset = 0.5f), DELTA)
        assertEquals(-0.0625f, shiftU(375, 625, pageOffset = 0f), DELTA)
        assertEquals(0.0625f, shiftU(375, 625, pageOffset = 1f), DELTA)
    }

    @Test
    fun `a crop at the left edge shows as chosen on the first page`() {
        assertEquals(0f, shiftU(0, 250, pageOffset = 0f), DELTA)
        assertEquals(0.125f, shiftU(0, 250, pageOffset = 1f), DELTA)
    }

    @Test
    fun `a crop at the right edge shows as chosen on the last page`() {
        assertEquals(-0.125f, shiftU(750, 1000, pageOffset = 0f), DELTA)
        assertEquals(0f, shiftU(750, 1000, pageOffset = 1f), DELTA)
    }

    @Test
    fun `a crop as wide as the frame doesn't move`() {
        assertEquals(0f, shiftU(0, 1000, pageOffset = 0f), DELTA)
        assertEquals(0f, shiftU(0, 1000, pageOffset = 1f), DELTA)
    }

    @Test
    fun `the window never leaves the frame`() {
        // Only 50 pixels of the frame are left beside a crop 800 wide.
        listOf(0f, 0.5f, 1f).forEach { offset ->
            val shift = shiftU(100, 900, offset)
            assertTrue(0.1f + shift >= -DELTA && 0.9f + shift <= 1f + DELTA, "out of the frame at $offset: $shift")
        }
    }

    @Test
    fun `an unturned crop pans only sideways`() {
        assertEquals(0f, PageScroll(1000, 1000, CropRect(375, 0, 625, 1000)).shift(0f).v, DELTA)
    }

    @Test
    fun `a crop turned a quarter clockwise pans along the frame's vertical`() {
        // The screen's left to right runs up the frame: the picture is turned clockwise.
        val scroll = PageScroll(1000, 1000, CropRect(left = 0, top = 375, right = 250, bottom = 625, rotation = 90f))

        val first = scroll.shift(0f)
        val last = scroll.shift(1f)

        assertEquals(0f, first.u, DELTA)
        assertEquals(0f, last.u, DELTA)
        // Texture v grows upwards, the same way the window goes.
        assertEquals(-0.0625f, first.v, DELTA)
        assertEquals(0.0625f, last.v, DELTA)
    }

    @Test
    fun `a turned crop keeps every corner in the frame`() {
        val crop = CropRect(left = 300, top = 300, right = 700, bottom = 700, rotation = 30f)
        val corners = CropCorners(crop).all
        val scroll = PageScroll(1000, 1000, crop)

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { offset ->
            val shift = scroll.shift(offset)
            corners.forEach { corner ->
                val x = corner.x + shift.u * 1000
                val y = corner.y - shift.v * 1000
                assertTrue(x in -0.01f..1000.01f && y in -0.01f..1000.01f, "($x, $y) out of the frame at $offset")
            }
        }
        // The window does move: a 400 wide crop turned by 30° has room both ways.
        assertTrue(scroll.shift(1f).u - scroll.shift(0f).u > 0f)
    }

    /** The horizontal shift of a crop from [left] to [right] across a 1000 x 1000 frame. */
    private fun shiftU(left: Int, right: Int, pageOffset: Float) =
        PageScroll(1000, 1000, CropRect(left, 0, right, 1000)).shift(pageOffset).u

    private companion object {
        const val DELTA = 1e-5f
    }
}
