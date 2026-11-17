package com.onthecrow.wallper.core.model

import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FramingTest {

    /** A portrait crop out of the middle of a 4K landscape video. */
    private val portrait = CropRect(left = 1380, top = 0, right = 2460, bottom = 2160)

    @Test
    fun `a crop in the screen's proportions is shown as it was saved`() {
        assertSame(portrait, portrait.reshaped(aspect = 1080f / 2160, FRAME_WIDTH, FRAME_HEIGHT))
        // Rounding of the saved pixels is not a change of proportions.
        val rounded = portrait.copy(right = 2461)
        assertSame(rounded, rounded.reshaped(aspect = 0.5f, FRAME_WIDTH, FRAME_HEIGHT))
    }

    @Test
    fun `a turned screen keeps the centre and the area in its own proportions`() {
        val landscape = portrait.copy(top = 540, bottom = 1620, left = 1380, right = 2460).reshaped(
            aspect = 2f, FRAME_WIDTH, FRAME_HEIGHT,
        )

        assertEquals(1920f, landscape.centerX, 0.5f)
        assertEquals(1080f, landscape.centerY, 0.5f)
        assertEquals(2f, landscape.width.toFloat() / landscape.height, 0.01f)
        assertEquals(1080f * 1080f, landscape.width.toFloat() * landscape.height, 1080f * 1080f * 0.01f)
    }

    @Test
    fun `a window that would leave the frame slides back into it`() {
        // A square at the left edge, made twice as wide as it is high.
        val square = CropRect(left = 0, top = 1000, right = 1000, bottom = 2000)

        val wide = square.reshaped(aspect = 2f, FRAME_WIDTH, FRAME_HEIGHT)

        assertEquals(0, wide.left)
        assertEquals(1500f, wide.centerY, 0.5f)
        assertEquals(2f, wide.width.toFloat() / wide.height, 0.01f)
    }

    @Test
    fun `a window too big for the frame gets smaller in its proportions`() {
        // All of a portrait video, turned to the landscape of a phone: it can't have the same area.
        val whole = CropRect(left = 0, top = 0, right = 1080, bottom = 1920)

        val landscape = whole.reshaped(aspect = 2400f / 1080, frameWidth = 1080, frameHeight = 1920)

        assertEquals(0, landscape.left)
        assertEquals(1080, landscape.right)
        assertEquals(2400f / 1080, landscape.width.toFloat() / landscape.height, 0.01f)
        assertEquals(960f, landscape.centerY, 1f)
    }

    @Test
    fun `a turned crop stays inside the frame with its corners`() {
        val tilted = CropRect(left = 1500, top = 200, right = 2300, bottom = 1800, rotation = 20f)

        val wide = tilted.reshaped(aspect = 2f, FRAME_WIDTH, FRAME_HEIGHT)

        assertEquals(20f, wide.rotation)
        corners(wide).forEach { (x, y) ->
            assertTrue(x >= -1f && x <= FRAME_WIDTH + 1f, "x $x")
            assertTrue(y >= -1f && y <= FRAME_HEIGHT + 1f, "y $y")
        }
    }

    @Test
    fun `a screen gets the crop nearer its proportions`() {
        val landscape = CropRect(left = 0, top = 540, right = 2160, bottom = 1620)
        val framing = Framing(crop = portrait, turned = landscape)

        assertSame(portrait, framing.forScreen(1080, 2160, FRAME_WIDTH, FRAME_HEIGHT))
        assertSame(landscape, framing.forScreen(2160, 1080, FRAME_WIDTH, FRAME_HEIGHT))
    }

    @Test
    fun `without a turned crop the other screen derives one`() {
        val framing = Framing(crop = portrait)

        val window = framing.forScreen(2160, 1080, FRAME_WIDTH, FRAME_HEIGHT)

        assertEquals(portrait.reshaped(2f, FRAME_WIDTH, FRAME_HEIGHT), window)
        assertEquals(2f, window.width.toFloat() / window.height, 0.01f)
    }

    @Test
    fun `the chosen crop is reshaped to the screen's exact proportions`() {
        // A foldable's inner screen is nearly square: neither crop has its proportions.
        val landscape = CropRect(left = 0, top = 540, right = 2160, bottom = 1620)
        val framing = Framing(crop = portrait, turned = landscape)

        val window = framing.forScreen(2200, 1900, FRAME_WIDTH, FRAME_HEIGHT)

        assertEquals(2200f / 1900, window.width.toFloat() / window.height, 0.01f)
        assertEquals(landscape.centerX, window.centerX, 1f)
    }

    @Test
    fun `a whole frame of one pixel stays whole on any screen`() {
        // An optimised video whose export's size is unknown: all of it over the whole screen.
        val whole = Framing(CropRect(0, 0, 1, 1))

        assertEquals(CropRect(0, 0, 1, 1), whole.forScreen(1080, 2400, frameWidth = 1, frameHeight = 1))
        assertEquals(CropRect(0, 0, 1, 1), whole.forScreen(2400, 1080, frameWidth = 1, frameHeight = 1))
    }

    private fun corners(rect: CropRect): List<Pair<Float, Float>> {
        val radians = Math.toRadians(rect.rotation.toDouble())
        val cos = cos(radians).toFloat()
        val sin = sin(radians).toFloat()
        return listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).map { (sx, sy) ->
            val dx = sx * rect.width / 2
            val dy = sy * rect.height / 2
            (rect.centerX + dx * cos + dy * sin) to (rect.centerY - dx * sin + dy * cos)
        }
    }

    private companion object {
        const val FRAME_WIDTH = 3840
        const val FRAME_HEIGHT = 2160
    }
}
