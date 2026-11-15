package com.onthecrow.wallper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TintTest {

    @Test
    fun `the channels are the bytes of the packed colour`() {
        val tint = Tint(0x80FF3300.toInt())

        assertEquals(128 / 255f, tint.alpha)
        assertEquals(1f, tint.red)
        assertEquals(0x33 / 255f, tint.green)
        assertEquals(0f, tint.blue)
    }

    @Test
    fun `a fully transparent tint is no tint`() {
        assertNull(Tint(0x00FF3300).orNull())
        assertEquals(Tint(0x01FF3300), Tint(0x01FF3300).orNull())
    }

    @Test
    fun `an opaque tint hides the picture and a transparent one leaves it`() {
        assertEquals(0.25f, Tint(0xFF000000.toInt()).over(channel = 0.9f, color = 0.25f))
        assertEquals(0.9f, Tint(0x00000000).over(channel = 0.9f, color = 0.25f))
    }

    @Test
    fun `black at a third darkens the picture by a third`() {
        val tint = Tint(0x55000000)

        assertEquals(0.6f, tint.over(channel = 0.9f, color = tint.red), absoluteTolerance = 1e-6f)
    }
}
