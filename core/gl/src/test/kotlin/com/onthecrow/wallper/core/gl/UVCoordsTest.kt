package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals

class UVCoordsTest {

    @Test
    fun `a full-height strip keeps its horizontal position`() {
        val uv = UVCoords.fromRect(200, 100, CropRect(left = 50, top = 0, right = 100, bottom = 100))

        assertEquals(UVPoint(0.25f, 1f), uv.topLeft)
        assertEquals(UVPoint(0.5f, 1f), uv.topRight)
        assertEquals(UVPoint(0.25f, 0f), uv.bottomLeft)
        assertEquals(UVPoint(0.5f, 0f), uv.bottomRight)
    }

    @Test
    fun `texture coordinates grow upwards`() {
        // The top quarter of the frame.
        val uv = UVCoords.fromRect(100, 200, CropRect(left = 0, top = 0, right = 100, bottom = 50))

        assertEquals(UVPoint(0f, 1f), uv.topLeft)
        assertEquals(UVPoint(1f, 0.75f), uv.bottomRight)
    }
}
