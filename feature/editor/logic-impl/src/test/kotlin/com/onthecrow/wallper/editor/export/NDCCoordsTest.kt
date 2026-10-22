package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals

class NDCCoordsTest {

    @Test
    fun `the whole frame spans minus one to one`() {
        assertEquals(
            NDCCoords(left = -1f, right = 1f, bottom = -1f, top = 1f),
            NDCCoords.fromRect(CropRect(0, 0, 200, 100), 200f, 100f),
        )
    }

    @Test
    fun `a crop in the top left quarter stays there`() {
        val ndc = NDCCoords.fromRect(CropRect(left = 10, top = 10, right = 50, bottom = 40), 200f, 100f)

        assertEquals(-0.9f, ndc.left, TOLERANCE)
        assertEquals(-0.5f, ndc.right, TOLERANCE)
        assertEquals(0.2f, ndc.bottom, TOLERANCE)
        assertEquals(0.8f, ndc.top, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
