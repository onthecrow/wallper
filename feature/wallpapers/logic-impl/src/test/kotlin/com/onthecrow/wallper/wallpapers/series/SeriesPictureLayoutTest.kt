package com.onthecrow.wallper.wallpapers.series

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeriesPictureLayoutTest {

    private val square = SeriesPictureLayout(count = 4, width = 540f, height = 540f)

    @Test
    fun `the lines share the width evenly at mid-height and lean right going up, all alike`() {
        val leans = (1 until square.count).map { index ->
            assertEquals(540f * index / 4, square.lineX(index, 270f), TOLERANCE)
            square.lineX(index, 0f) - square.lineX(index, 540f)
        }
        assertTrue(leans.first() > 0f)
        leans.forEach { assertEquals(leans.first(), it, TOLERANCE) }
    }

    @Test
    fun `on a square each line runs from the top edge to the bottom one`() {
        (1 until square.count).forEach { index ->
            assertTrue(square.lineX(index, 0f) in 0f..540f)
            assertTrue(square.lineX(index, 540f) in 0f..540f)
        }
    }

    @Test
    fun `each part's picture covers the whole of its part and stays inside`() {
        for (layout in listOf(square, SeriesPictureLayout(2, 540f, 540f), SeriesPictureLayout(3, 300f, 600f))) {
            (0 until layout.count).forEach { index ->
                val left = layout.boxLeft(index)
                val right = layout.boxRight(index)
                assertTrue(left >= 0f && right <= layout.width && left < right)
                // Where the part's edges are inside the picture: a tall one's lines may leave it.
                for (y in listOf(0f, layout.height / 2, layout.height)) {
                    if (index > 0) assertTrue(layout.lineX(index, y).coerceIn(0f, layout.width) >= left - TOLERANCE)
                    if (index < layout.count - 1) {
                        assertTrue(layout.lineX(index + 1, y).coerceIn(0f, layout.width) <= right + TOLERANCE)
                    }
                }
            }
        }
    }

    @Test
    fun `one picture fills it all`() {
        val single = SeriesPictureLayout(1, 540f, 540f)

        assertEquals(0f, single.boxLeft(0))
        assertEquals(540f, single.boxRight(0))
    }

    private companion object {
        const val TOLERANCE = 1e-3f
    }
}
