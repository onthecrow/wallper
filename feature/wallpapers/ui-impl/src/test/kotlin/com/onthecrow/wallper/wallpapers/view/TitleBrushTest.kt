package com.onthecrow.wallper.wallpapers.view

import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitleBrushTest {

    private data class Piece(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val reach: Float) {
        val length get() = hypot(x1 - x0, y1 - y0)
    }

    private fun trace(from: Float, to: Float) = buildList {
        TitleBrush.trace(from, to) { x0, y0, x1, y1, reach -> add(Piece(x0, y0, x1, y1, reach)) }
    }

    @Test
    fun `the whole way is the brush's length, inside the lettering's viewport`() {
        val pieces = trace(0f, 1f)

        assertEquals(TitleBrush.length, pieces.sumOf { it.length.toDouble() }.toFloat(), 0.01f)
        assertTrue(pieces.all { it.x0 in 0f..TitleBrush.VIEWPORT_WIDTH && it.y0 in 0f..TitleBrush.VIEWPORT_HEIGHT })
        assertTrue(pieces.all { it.reach > 0f })
    }

    @Test
    fun `a moment's part and the rest make up the whole way`() {
        val written = trace(0f, 0.4f).sumOf { it.length.toDouble() }
        val rest = trace(0.4f, 1f).sumOf { it.length.toDouble() }

        assertEquals(0.4 * TitleBrush.length, written, 0.01)
        assertEquals(TitleBrush.length.toDouble(), written + rest, 0.01)
    }

    @Test
    fun `nothing between the same two moments, nor backwards`() {
        assertEquals(emptyList(), trace(0.3f, 0.3f))
        assertEquals(emptyList(), trace(0.6f, 0.2f))
    }

    @Test
    fun `it writes from the left`() {
        val first = trace(0f, 0.05f)
        val last = trace(0.95f, 1f)

        assertTrue(first.maxOf { maxOf(it.x0, it.x1) } < last.minOf { minOf(it.x0, it.x1) })
    }
}
