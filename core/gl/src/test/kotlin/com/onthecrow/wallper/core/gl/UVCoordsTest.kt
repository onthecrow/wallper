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

    @Test
    fun `a quarter turn clockwise shows the frame's bottom left corner at the top left`() {
        // A 100 x 50 window around the centre of a 200 x 200 frame, the picture turned clockwise.
        val uv = UVCoords.fromRect(200, 200, CropRect(left = 50, top = 75, right = 150, bottom = 125, rotation = 90f))

        // The window covers 50 x 100 pixels of the frame, standing up: x 75..125, y 50..150.
        assertUv(UVPoint(0.375f, 0.25f), uv.topLeft)
        assertUv(UVPoint(0.375f, 0.75f), uv.topRight)
        assertUv(UVPoint(0.625f, 0.25f), uv.bottomLeft)
        assertUv(UVPoint(0.625f, 0.75f), uv.bottomRight)
    }

    private fun assertUv(expected: UVPoint, actual: UVPoint) {
        assertEquals(expected.u, actual.u, 1e-5f)
        assertEquals(expected.v, actual.v, 1e-5f)
    }
}
