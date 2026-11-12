package com.onthecrow.wallper.editor.crop

import com.onthecrow.wallper.core.model.CropRect
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CropGeometryTest {

    // A landscape video under a phone's portrait frame.
    private val geometry = CropGeometry(mediaWidth = 1920f, mediaHeight = 1080f, frameWidth = 450f, frameHeight = 1000f)

    @Test
    fun `at first the frame shows the middle of the media at full height`() {
        assertEquals(CropRect(left = 717, top = 0, right = 1203, bottom = 1080), geometry.toCrop(geometry.initial))
    }

    @Test
    fun `a picture zoomed out below the frame grows back to cover it`() {
        val covering = geometry.covering(geometry.initial.copy(scale = geometry.baseScale / 2))

        assertEquals(geometry.baseScale, covering.scale, TOLERANCE)
        assertEquals(0f, covering.offsetX, TOLERANCE)
        assertEquals(0f, covering.offsetY, TOLERANCE)
    }

    @Test
    fun `a picture dragged off the frame comes back only as far as it must`() {
        val covering = geometry.covering(geometry.initial.copy(offsetX = 1_000f, offsetY = -30f))

        assertEquals(geometry.baseScale, covering.scale, TOLERANCE)
        // Its left edge meets the frame's: half the picture's width minus half the frame's.
        assertEquals(1920f / 2 * geometry.baseScale - 450f / 2, covering.offsetX, TOLERANCE)
        // It fills the frame's height, so it can't move up or down at all.
        assertEquals(0f, covering.offsetY, TOLERANCE)
        assertEquals(0, geometry.toCrop(covering).left)
    }

    @Test
    fun `a picture that covers the frame stays where it is`() {
        val placement = Placement(scale = geometry.baseScale * 2, rotation = 0f, offsetX = 120f, offsetY = -200f)

        assertEquals(placement, geometry.covering(placement))
    }

    @Test
    fun `a turned picture grows and moves until the frame's corners are all inside the media`() {
        val covering = geometry.covering(Placement(scale = geometry.baseScale, rotation = 30f, offsetX = 300f, offsetY = 0f))

        assertTrue(covering.scale > geometry.baseScale)
        assertEquals(30f, covering.rotation)
        frameCorners().forEach { (x, y) ->
            val (mediaX, mediaY) = mediaPointAt(covering, x, y)
            assertTrue(mediaX in -EDGE..1920f + EDGE && mediaY in -EDGE..1080f + EDGE, "($mediaX, $mediaY) is outside")
        }
    }

    @Test
    fun `zooming in stops at the limit`() {
        val tooClose = geometry.initial.copy(scale = geometry.baseScale * 20)

        assertEquals(geometry.baseScale * CropGeometry.MAX_ZOOM, geometry.covering(tooClose).scale, TOLERANCE)
    }

    @Test
    fun `a gesture keeps the point under the fingers`() {
        val placement = Placement(scale = geometry.baseScale * 2, rotation = 10f, offsetX = 40f, offsetY = -60f)
        val before = mediaPointAt(placement, 100f, 200f)

        val after = geometry.transformed(placement, 100f, 200f, panX = 30f, panY = -15f, zoom = 1.5f, rotation = 25f)

        assertEquals(35f, after.rotation, TOLERANCE)
        assertEquals(placement.scale * 1.5f, after.scale, TOLERANCE)
        val (x, y) = mediaPointAt(after, 130f, 185f)
        assertEquals(before.first, x, 0.01f)
        assertEquals(before.second, y, 0.01f)
    }

    @Test
    fun `a gesture can't shrink the picture to nothing`() {
        val after = geometry.transformed(geometry.initial, 0f, 0f, 0f, 0f, zoom = 0.01f, rotation = 0f)

        assertEquals(geometry.baseScale / 2, after.scale, TOLERANCE)
    }

    @Test
    fun `a crop comes back as the placement it was made from`() {
        val placement = geometry.covering(Placement(scale = geometry.baseScale * 1.7f, rotation = -20f, offsetX = 50f, offsetY = 90f))

        val restored = geometry.fromCrop(geometry.toCrop(placement))

        assertEquals(placement.rotation, restored.rotation, TOLERANCE)
        assertEquals(placement.scale, restored.scale, placement.scale * 0.01f)
        assertEquals(placement.offsetX, restored.offsetX, 2f)
        assertEquals(placement.offsetY, restored.offsetY, 2f)
    }

    @Test
    fun `the crop's rotation stays within half a turn either way`() {
        assertEquals(10f, geometry.toCrop(geometry.covering(geometry.initial.copy(rotation = 370f))).rotation, TOLERANCE)
        assertEquals(-90f, geometry.toCrop(geometry.covering(geometry.initial.copy(rotation = 270f))).rotation, TOLERANCE)
    }

    @Test
    fun `a crop of other proportions shows its middle as large as the frame allows`() {
        // Chosen on a wider screen: as tall as the media, wider than this frame's crop would be.
        val crop = geometry.toCrop(geometry.fromCrop(CropRect(left = 0, top = 0, right = 729, bottom = 1080)))

        assertEquals(1080, crop.height)
        assertTrue(crop.width in 485..487, "$crop")
        assertTrue(crop.centerX in 364f..365f, "$crop")
    }

    @Test
    fun `an empty crop starts from the beginning`() {
        assertEquals(geometry.initial, geometry.fromCrop(CropRect(0, 0, 0, 0)))
    }

    @Test
    fun `an angle near a quarter turn snaps to it`() {
        assertEquals(0f, CropGeometry.snappedToLevel(3.9f))
        assertEquals(0f, CropGeometry.snappedToLevel(-3.9f))
        assertEquals(90f, CropGeometry.snappedToLevel(87f))
        assertEquals(-180f, CropGeometry.snappedToLevel(-183f))
        assertEquals(5f, CropGeometry.snappedToLevel(5f))
        assertEquals(45f, CropGeometry.snappedToLevel(45f))
    }

    @Test
    fun `a centre near the frame's middle snaps to it along each axis on its own`() {
        // 3% of the frame's shorter side, 450: 13.5 pixels.
        val snapped = geometry.snapped(Placement(scale = 1f, rotation = 2f, offsetX = 13f, offsetY = -14f))

        assertEquals(Placement(scale = 1f, rotation = 0f, offsetX = 0f, offsetY = -14f), snapped)
        assertEquals(Snaps(level = true, centerX = true, centerY = false), geometry.snapsOf(snapped))
    }

    @Test
    fun `a new snap is one that wasn't there before`() {
        val centred = Snaps(level = false, centerX = true, centerY = true)

        assertTrue(Snaps(level = true, centerX = true, centerY = true).gainedSince(centred))
        assertFalse(Snaps(level = false, centerX = true, centerY = false).gainedSince(centred))
    }

    @Test
    fun `level means a whole number of quarter turns`() {
        assertTrue(CropGeometry.isLevel(0f))
        assertTrue(CropGeometry.isLevel(-270f))
        assertFalse(CropGeometry.isLevel(0.5f))
    }

    /** The media pixel shown at [x], [y] view pixels from the frame's centre. */
    private fun mediaPointAt(placement: Placement, x: Float, y: Float): Pair<Float, Float> {
        val radians = Math.toRadians(-placement.rotation.toDouble())
        val dx = (x - placement.offsetX) / placement.scale
        val dy = (y - placement.offsetY) / placement.scale
        return Pair(
            1920f / 2 + dx * cos(radians).toFloat() - dy * sin(radians).toFloat(),
            1080f / 2 + dx * sin(radians).toFloat() + dy * cos(radians).toFloat(),
        )
    }

    private fun frameCorners() = listOf(-225f to -500f, 225f to -500f, -225f to 500f, 225f to 500f)

    private companion object {
        const val TOLERANCE = 1e-3f

        /** Rounding the crop to pixels may put a corner this far past an edge. */
        const val EDGE = 0.01f
    }
}
