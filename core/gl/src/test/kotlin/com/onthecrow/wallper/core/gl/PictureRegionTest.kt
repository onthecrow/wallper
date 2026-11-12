package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PictureRegionTest {

    @Test
    fun `an unturned crop in the middle needs itself and the page scroll's travel either side`() {
        // 250 wide: the window travels 125 pixels, half each way, plus the margin.
        val region = PictureRegion(1000, 1000, CropRect(375, 0, 625, 1000))

        assertEquals(listOf(310, 0, 690, 1000), listOf(region.left, region.top, region.right, region.bottom))
    }

    @Test
    fun `a turned crop's window stays inside the region on every page`() {
        val crop = CropRect(left = 300, top = 300, right = 700, bottom = 700, rotation = 30f)
        val region = PictureRegion(1000, 1000, crop)
        val corners = CropCorners(crop).all
        val scroll = PageScroll(1000, 1000, crop)

        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { offset ->
            val shift = scroll.shift(offset)
            corners.forEach { corner ->
                val x = corner.x + shift.u * 1000
                val y = corner.y - shift.v * 1000
                assertTrue(x >= region.left && x <= region.right && y >= region.top && y <= region.bottom, "($x, $y) at $offset")
            }
        }
    }

    @Test
    fun `the texture matrix takes the region's corners to the texture's`() {
        val region = PictureRegion(1000, 800, CropRect(400, 100, 600, 500))
        val matrix = region.textureMatrix()

        // The region's top left in frame coordinates (v up) is the bitmap's first pixel, its bottom right the last.
        assertPoint(0f, 0f, matrix, u = region.left / 1000f, v = 1f - region.top / 800f)
        assertPoint(1f, 1f, matrix, u = region.right / 1000f, v = 1f - region.bottom / 800f)
    }

    @Test
    fun `a landscape photo's window pans across its texture with the pages`() {
        // A 4:3 photo under a 9:20 screen: the crop, in the middle, has room either side.
        val crop = CropRect(1325, 0, 2675, 3000)
        val region = PictureRegion(4000, 3000, crop)
        val matrix = region.textureMatrix()
        val scroll = PageScroll(4000, 3000, crop)
        val left = UVCoords.fromRect(4000, 3000, crop).topLeft

        fun textureU(offset: Float) = matrix[0] * (left.u + scroll.shift(offset).u) + matrix[4] * left.v + matrix[12]

        // From the region's left edge on the first page, half the travel on, to as far again on the last.
        assertEquals(0f, textureU(0f), 0.002f)
        assertTrue(textureU(0.5f) > textureU(0f) && textureU(1f) > textureU(0.5f))
        assertEquals(textureU(0.5f) - textureU(0f), textureU(1f) - textureU(0.5f), DELTA)
    }

    @Test
    fun `the sample keeps a pixel of the crop for each of the screen's, and fits the texture`() {
        val region = PictureRegion(4000, 4000, CropRect(0, 0, 4000, 4000))

        assertEquals(1, region.sampleSize(CropRect(0, 0, 1500, 3000), 1080, 2400, maxTextureSize = 8192))
        assertEquals(2, region.sampleSize(CropRect(0, 0, 2400, 4000), 1080, 1920, maxTextureSize = 8192))
        // 4000 / 2 doesn't fit in 1024: 4000 / 4 does.
        assertEquals(4, region.sampleSize(CropRect(0, 0, 2400, 4000), 1080, 1920, maxTextureSize = 1024))
    }

    private fun assertPoint(s: Float, t: Float, matrix: FloatArray, u: Float, v: Float) {
        // Column-major: s = m0 u + m4 v + m12, t = m1 u + m5 v + m13.
        assertEquals(s, matrix[0] * u + matrix[4] * v + matrix[12], DELTA)
        assertEquals(t, matrix[1] * u + matrix[5] * v + matrix[13], DELTA)
    }

    private companion object {
        const val DELTA = 1e-4f
    }
}
