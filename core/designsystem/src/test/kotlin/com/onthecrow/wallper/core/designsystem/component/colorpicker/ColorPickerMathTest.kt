package com.onthecrow.wallper.core.designsystem.component.colorpicker

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorPickerMathTest {

    // The card is x 20..320, the area y 10..110, the hue bar to 140, the alpha bar to 170.
    private val geometry = ColorPickerGeometry(
        width = 340f,
        height = 175f,
        barHeight = 30f,
        insetX = 20f,
        insetTop = 10f,
        insetBottom = 5f,
    )
    private val color = HsvColor(hue = 90f, saturation = 0.5f, value = 0.25f, alpha = 0.75f)

    @Test
    fun `the parts are stacked without gaps`() {
        assertEquals(20f, geometry.left)
        assertEquals(320f, geometry.right)
        assertEquals(10f, geometry.top)
        assertEquals(110f, geometry.hueTop)
        assertEquals(140f, geometry.alphaTop)
        assertEquals(170f, geometry.alphaBottom)
    }

    @Test
    fun `a touch starts on the part under it, the insets going to the nearest`() {
        assertEquals(PickerPart.SaturationValue, geometry.partAt(0f))
        assertEquals(PickerPart.SaturationValue, geometry.partAt(109f))
        assertEquals(PickerPart.Hue, geometry.partAt(110f))
        assertEquals(PickerPart.Hue, geometry.partAt(139f))
        assertEquals(PickerPart.Alpha, geometry.partAt(140f))
        assertEquals(PickerPart.Alpha, geometry.partAt(174f))
    }

    @Test
    fun `the area sets saturation across and value upwards, and nothing else`() {
        assertEquals(
            color.copy(saturation = 0.25f, value = 0.75f),
            geometry.colorAt(PickerPart.SaturationValue, x = 95f, y = 35f, color),
        )
    }

    @Test
    fun `the corners of the area are white, the pure hue and black`() {
        val topLeft = geometry.colorAt(PickerPart.SaturationValue, x = 20f, y = 10f, color)
        val topRight = geometry.colorAt(PickerPart.SaturationValue, x = 320f, y = 10f, color)
        val bottom = geometry.colorAt(PickerPart.SaturationValue, x = 170f, y = 110f, color)

        assertEquals(0f to 1f, topLeft.saturation to topLeft.value)
        assertEquals(1f to 1f, topRight.saturation to topRight.value)
        assertEquals(0f, bottom.value)
    }

    @Test
    fun `the bars set the hue and the alpha across`() {
        assertEquals(color.copy(hue = 180f), geometry.colorAt(PickerPart.Hue, x = 170f, y = 125f, color))
        assertEquals(color.copy(alpha = 0.5f), geometry.colorAt(PickerPart.Alpha, x = 170f, y = 155f, color))
    }

    @Test
    fun `a drag that leaves its part stays at the part's edge`() {
        val dragged = geometry.colorAt(PickerPart.SaturationValue, x = 1_000f, y = 1_000f, color)

        assertEquals(1f to 0f, dragged.saturation to dragged.value)
        assertEquals(0f, geometry.colorAt(PickerPart.Alpha, x = -50f, y = 0f, color).alpha)
        assertEquals(HsvColor.MAX_HUE, geometry.colorAt(PickerPart.Hue, x = 400f, y = 0f, color).hue)
    }

    @Test
    fun `the thumbs are where their colour is picked`() {
        assertEquals(170f, geometry.saturationX(color))
        assertEquals(85f, geometry.valueY(color))
        assertEquals(95f, geometry.hueX(color))
        assertEquals(245f, geometry.alphaX(color))

        val picked = geometry.colorAt(PickerPart.SaturationValue, geometry.saturationX(color), geometry.valueY(color), color)
        assertEquals(color, picked)
    }

    @Test
    fun `a picker too small for its insets picks nothing wild`() {
        val tiny = ColorPickerGeometry(width = 10f, height = 10f, barHeight = 30f, insetX = 20f, insetTop = 10f, insetBottom = 5f)

        val picked = tiny.colorAt(PickerPart.SaturationValue, x = 5f, y = 5f, color)

        assertEquals(0f, picked.saturation)
        assertEquals(1f, picked.value)
        assertEquals(tiny.left, tiny.saturationX(color))
    }
}
