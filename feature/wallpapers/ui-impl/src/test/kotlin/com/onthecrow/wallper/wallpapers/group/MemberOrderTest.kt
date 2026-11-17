package com.onthecrow.wallper.wallpapers.group

import kotlin.test.Test
import kotlin.test.assertEquals

class MemberOrderTest {

    @Test
    fun `a member dragged onto another takes its place, the ones between make way`() {
        assertEquals(listOf(2, 1, 3), MemberOrder.moved(listOf(1, 2, 3), id = 1, to = 2))
        assertEquals(listOf(1, 3, 2), MemberOrder.moved(listOf(1, 2, 3), id = 3, to = 2))
        assertEquals(listOf(2, 3, 1), MemberOrder.moved(listOf(1, 2, 3), id = 1, to = 3))
    }

    @Test
    fun `a member onto itself, or one that isn't there, changes nothing`() {
        assertEquals(listOf(1, 2, 3), MemberOrder.moved(listOf(1, 2, 3), id = 2, to = 2))
        assertEquals(listOf(1, 2, 3), MemberOrder.moved(listOf(1, 2, 3), id = 9, to = 2))
    }

    /** The list shows 0..1000 px; the zone is 100 px, the top speed 800 px a second. */
    private fun speed(top: Float, bottom: Float, movedDown: Boolean) =
        MemberOrder.autoScrollSpeed(top, bottom, movedDown, viewStart = 0f, viewEnd = 1_000f, zone = 100f, maxPxPerSecond = 800f)

    @Test
    fun `held near the edge it moves towards, the list scrolls, faster the nearer`() {
        assertEquals(-400f, speed(top = 50f, bottom = 170f, movedDown = false))
        assertEquals(-800f, speed(top = -20f, bottom = 100f, movedDown = false))
        assertEquals(400f, speed(top = 830f, bottom = 950f, movedDown = true))
    }

    @Test
    fun `away from the edges, or near the one it moves away from, the list stays`() {
        assertEquals(0f, speed(top = 400f, bottom = 520f, movedDown = true))
        assertEquals(0f, speed(top = 50f, bottom = 170f, movedDown = true))
        assertEquals(0f, speed(top = 830f, bottom = 950f, movedDown = false))
    }
}
