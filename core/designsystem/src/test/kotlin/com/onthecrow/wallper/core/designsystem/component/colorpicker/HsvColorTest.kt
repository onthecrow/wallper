package com.onthecrow.wallper.core.designsystem.component.colorpicker

import kotlin.test.Test
import kotlin.test.assertEquals

class HsvColorTest {

    @Test
    fun `the primary colours and their mixes are a sixth of the circle apart`() {
        assertEquals(0xFFFF0000.toInt(), HsvColor(hue = 0f, saturation = 1f, value = 1f).toArgb())
        assertEquals(0xFFFFFF00.toInt(), HsvColor(hue = 60f, saturation = 1f, value = 1f).toArgb())
        assertEquals(0xFF00FF00.toInt(), HsvColor(hue = 120f, saturation = 1f, value = 1f).toArgb())
        assertEquals(0xFF00FFFF.toInt(), HsvColor(hue = 180f, saturation = 1f, value = 1f).toArgb())
        assertEquals(0xFF0000FF.toInt(), HsvColor(hue = 240f, saturation = 1f, value = 1f).toArgb())
        assertEquals(0xFFFF00FF.toInt(), HsvColor(hue = 300f, saturation = 1f, value = 1f).toArgb())
    }

    @Test
    fun `the end of the hue bar is red again`() {
        assertEquals(0xFFFF0000.toInt(), HsvColor(hue = HsvColor.MAX_HUE, saturation = 1f, value = 1f).toArgb())
    }

    @Test
    fun `no saturation is a grey and no value is black, whatever the hue`() {
        assertEquals(0xFF808080.toInt(), HsvColor(hue = 200f, saturation = 0f, value = 128 / 255f).toArgb())
        assertEquals(0xFF000000.toInt(), HsvColor(hue = 200f, saturation = 0.7f, value = 0f).toArgb())
        assertEquals(0xFFFFFFFF.toInt(), HsvColor(hue = 200f, saturation = 0f, value = 1f).toArgb())
    }

    @Test
    fun `alpha goes into the top byte`() {
        assertEquals(0x4D000000, HsvColor(hue = 0f, saturation = 0f, value = 0f, alpha = 0.3f).toArgb())
        assertEquals(0x00FF0000, HsvColor(hue = 0f, saturation = 1f, value = 1f, alpha = 0f).toArgb())
    }

    @Test
    fun `a packed colour is taken apart`() {
        val color = HsvColor.fromArgb(0x80336699.toInt())

        assertEquals(210f, color.hue, absoluteTolerance = 0.01f)
        assertEquals(2 / 3f, color.saturation, absoluteTolerance = 1e-4f)
        assertEquals(0.6f, color.value, absoluteTolerance = 1e-4f)
        assertEquals(128 / 255f, color.alpha)
    }

    @Test
    fun `a hue between magenta and red stays on the circle`() {
        val color = HsvColor.fromArgb(0xFFFF0080.toInt())

        assertEquals(329.88f, color.hue, absoluteTolerance = 0.01f)
    }

    @Test
    fun `every packed colour comes back as it was`() {
        // A saved tint is shown by the picker and saved again: it must not drift.
        val steps = (0..255 step 15) + listOf(1, 2, 127, 128, 254)
        for (red in steps) for (green in steps) for (blue in steps) {
            val argb = (0xC8 shl 24) or (red shl 16) or (green shl 8) or blue
            assertEquals(argb, HsvColor.fromArgb(argb).toArgb(), "0x${argb.toUInt().toString(16)}")
        }
        for (alpha in 0..255) {
            val argb = (alpha shl 24) or 0x336699
            assertEquals(argb, HsvColor.fromArgb(argb).toArgb())
        }
    }
}
