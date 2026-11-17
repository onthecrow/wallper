package com.onthecrow.wallper.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TintTest {

    @Test
    fun `a fully transparent tint is no tint`() {
        assertNull(Tint(0x00FF3300).orNull())
        assertEquals(Tint(0x01FF3300), Tint(0x01FF3300).orNull())
    }

    @Test
    fun `black at a third takes a third of every channel away`() {
        val tint = Tint(0x55000000)

        assertEquals(2 / 3f, tint.redFactor, TOLERANCE)
        assertEquals(2 / 3f, tint.greenFactor, TOLERANCE)
        assertEquals(2 / 3f, tint.blueFactor, TOLERANCE)
        assertEquals(0xFFAAAAAA.toInt(), tint.multiplierArgb)
    }

    @Test
    fun `white changes nothing, however strong`() {
        val tint = Tint(0xFFFFFFFF.toInt())

        assertEquals(listOf(1f, 1f, 1f), listOf(tint.redFactor, tint.greenFactor, tint.blueFactor))
        assertEquals(0xFFFFFFFF.toInt(), tint.multiplierArgb)
    }

    @Test
    fun `a colour at full strength lets only itself through`() {
        val tint = Tint(0xFFFF8000.toInt())

        assertEquals(1f, tint.redFactor)
        assertEquals(128 / 255f, tint.greenFactor, TOLERANCE)
        assertEquals(0f, tint.blueFactor)
        assertEquals(0xFFFF8000.toInt(), tint.multiplierArgb)
    }

    @Test
    fun `a weaker tint takes less away`() {
        val tint = Tint(0x80FF8000.toInt())

        assertEquals(1f, tint.redFactor)
        assertEquals(1 - 128 / 255f * (1 - 128 / 255f), tint.greenFactor, TOLERANCE)
        assertEquals(1 - 128 / 255f, tint.blueFactor, TOLERANCE)
    }

    @Test
    fun `no tint ever lightens the picture`() {
        for (argb in listOf(0x4D000000, 0xFFFFFFFF.toInt(), 0x80336699.toInt(), 0x01FFFF00, 0xFF00FF00.toInt())) {
            val tint = Tint(argb)
            assertTrue(listOf(tint.redFactor, tint.greenFactor, tint.blueFactor).all { it in 0f..1f }, "$argb")
        }
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
