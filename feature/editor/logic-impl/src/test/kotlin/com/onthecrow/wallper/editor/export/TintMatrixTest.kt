package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.Tint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TintMatrixTest {

    @Test
    fun `a pixel comes out as the tint over it`() {
        val tint = Tint(0x80FF8000.toInt())
        val pixel = floatArrayOf(0.2f, 0.5f, 0.9f)

        val out = TintMatrix(tint).toGlArray().times(pixel)

        assertEquals(tint.over(0.2f, tint.red), out[0], TOLERANCE)
        assertEquals(tint.over(0.5f, tint.green), out[1], TOLERANCE)
        assertEquals(tint.over(0.9f, tint.blue), out[2], TOLERANCE)
    }

    @Test
    fun `an opaque tint leaves only its colour`() {
        val out = TintMatrix(Tint(0xFF336699.toInt())).toGlArray().times(floatArrayOf(0.7f, 0.1f, 0.4f))

        assertContentEquals(floatArrayOf(0x33 / 255f, 0x66 / 255f, 0x99 / 255f), out)
    }

    @Test
    fun `black at a third only darkens`() {
        val matrix = TintMatrix(Tint(0x55000000))

        assertEquals(2 / 3f, matrix.keep, TOLERANCE)
        assertContentEquals(floatArrayOf(0f, 0f, 0f), floatArrayOf(matrix.addRed, matrix.addGreen, matrix.addBlue))
    }

    /** The column-major matrix times `(r, g, b, 1)`, as the export's shader does it; its rgb. */
    private fun FloatArray.times(rgb: FloatArray): FloatArray {
        val vector = floatArrayOf(rgb[0], rgb[1], rgb[2], 1f)
        return FloatArray(3) { row -> (0..3).sumOf { column -> (this[column * 4 + row] * vector[column]).toDouble() }.toFloat() }
    }

    private companion object {
        const val TOLERANCE = 1e-6f
    }
}
