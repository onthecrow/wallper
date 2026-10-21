package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals

class UVCoordsTest {

    // Left half of a 200x100 landscape frame.
    private val rect = CropRect(left = 0, top = 0, right = 100, bottom = 100)

    @Test
    fun `no rotation maps the rect straight onto the texture`() {
        val uv = UVCoords.fromRect(200, 100, rect, 0)

        assertEquals(UVPoint(0f, 0f), uv.topLeft)
        assertEquals(UVPoint(0.5f, 0f), uv.topRight)
        assertEquals(UVPoint(0f, 1f), uv.bottomLeft)
        assertEquals(UVPoint(0.5f, 1f), uv.bottomRight)
    }

    @Test
    fun `180 degrees flips both axes`() {
        val uv = UVCoords.fromRect(200, 100, rect, 180)

        assertEquals(UVPoint(0.5f, 1f), uv.topLeft)
        assertEquals(UVPoint(0f, 0f), uv.bottomRight)
    }

    @Test
    fun `90 and 270 degrees swap the frame sides`() {
        // A portrait video stored as 200x100 with rotation 90 is shown as 100x200.
        val portraitRect = CropRect(left = 0, top = 0, right = 100, bottom = 100)

        val uv90 = UVCoords.fromRect(200, 100, portraitRect, 90)
        assertEquals(UVPoint(0f, 1f), uv90.topLeft)
        assertEquals(UVPoint(0.5f, 0f), uv90.bottomRight)

        val uv270 = UVCoords.fromRect(200, 100, portraitRect, 270)
        assertEquals(UVPoint(0.5f, 0f), uv270.topLeft)
        assertEquals(UVPoint(0f, 1f), uv270.bottomRight)
    }
}
