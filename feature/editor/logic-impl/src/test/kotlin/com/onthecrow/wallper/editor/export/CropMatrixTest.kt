package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals

class CropMatrixTest {

    @Test
    fun `the whole frame stays as it is`() {
        val matrix = CropMatrix(CropRect(0, 0, 200, 100), 200f, 100f)

        assertMaps(matrix, from = -1f to 1f, to = -1f to 1f)
        assertMaps(matrix, from = 1f to -1f, to = 1f to -1f)
    }

    @Test
    fun `a crop in the top left quarter fills the output`() {
        // The crop's corners in input NDC: x -0.9..-0.5, y 0.2..0.8.
        val matrix = CropMatrix(CropRect(left = 10, top = 10, right = 50, bottom = 40), 200f, 100f)

        assertMaps(matrix, from = -0.9f to 0.8f, to = -1f to 1f)
        assertMaps(matrix, from = -0.5f to 0.2f, to = 1f to -1f)
        assertMaps(matrix, from = -0.7f to 0.5f, to = 0f to 0f)
    }

    @Test
    fun `a quarter turn clockwise puts the crop's bottom left corner at the top left`() {
        // 100 x 50 on the screen, turned: it covers x 75..125, y 50..150 of the 200 x 200 frame.
        val matrix = CropMatrix(CropRect(left = 50, top = 75, right = 150, bottom = 125, rotation = 90f), 200f, 200f)

        // The frame's (75, 150), its bottom left, is NDC (-0.25, -0.5).
        assertMaps(matrix, from = -0.25f to -0.5f, to = -1f to 1f)
        // The frame's (75, 50), its top left, ends at the top right.
        assertMaps(matrix, from = -0.25f to 0.5f, to = 1f to 1f)
        // The frame's (125, 150) at the bottom left.
        assertMaps(matrix, from = 0.25f to -0.5f, to = -1f to -1f)
    }

    @Test
    fun `the GL array is column-major`() {
        val matrix = CropMatrix(CropRect(left = 10, top = 20, right = 90, bottom = 60, rotation = 30f), 100f, 100f)
        val gl = matrix.toGlArray()

        assertEquals(matrix.xx, gl[0])
        assertEquals(matrix.yx, gl[1])
        assertEquals(matrix.xy, gl[4])
        assertEquals(matrix.yy, gl[5])
        assertEquals(matrix.tx, gl[12])
        assertEquals(matrix.ty, gl[13])
    }

    private fun assertMaps(matrix: CropMatrix, from: Pair<Float, Float>, to: Pair<Float, Float>) {
        val (x, y) = from
        assertEquals(to.first, matrix.xx * x + matrix.xy * y + matrix.tx, TOLERANCE, "x of $from")
        assertEquals(to.second, matrix.yx * x + matrix.yy * y + matrix.ty, TOLERANCE, "y of $from")
    }

    private companion object {
        const val TOLERANCE = 1e-5f
    }
}
