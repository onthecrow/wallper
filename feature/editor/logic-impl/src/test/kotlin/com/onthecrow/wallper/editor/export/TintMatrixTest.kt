package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.Tint
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TintMatrixTest {

    @Test
    fun `a pixel comes out multiplied by the tint's factors`() {
        val tint = Tint(0x80FF8000.toInt())

        val out = TintMatrix(tint).toGlArray().times(floatArrayOf(0.2f, 0.5f, 0.9f))

        assertEquals(0.2f * tint.redFactor, out[0], TOLERANCE)
        assertEquals(0.5f * tint.greenFactor, out[1], TOLERANCE)
        assertEquals(0.9f * tint.blueFactor, out[2], TOLERANCE)
    }

    @Test
    fun `black at full strength leaves nothing, and white leaves everything`() {
        val pixel = floatArrayOf(0.7f, 0.1f, 0.4f)

        assertContentEquals(floatArrayOf(0f, 0f, 0f), TintMatrix(Tint(0xFF000000.toInt())).toGlArray().times(pixel))
        assertContentEquals(pixel, TintMatrix(Tint(0xFFFFFFFF.toInt())).toGlArray().times(pixel))
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
