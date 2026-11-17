package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Framing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExportAreaTest {

    /** A phone's portrait crop in the middle of a square video. */
    private val portrait = CropRect(left = 1460, top = 800, right = 2540, bottom = 3200)

    @Test
    fun `without a turned crop the export holds the crop and the one derived from it`() {
        val area = ExportArea.of(Framing(portrait), FRAME, FRAME)

        // The landscape window of the same area and centre is 2400 x 1080: the two make a square.
        assertEquals(CropRect(800, 800, 3200, 3200), area.rect)
        assertEquals(CropRect(660, 0, 1740, 2400), area.framing.crop)
        // Derived again where it is played, the same way.
        assertNull(area.framing.turned)
    }

    @Test
    fun `a turned crop of its own is a window of the export where it was chosen`() {
        val landscape = CropRect(left = 0, top = 2000, right = 2400, bottom = 3080)

        val area = ExportArea.of(Framing(portrait, landscape), FRAME, FRAME)

        assertEquals(CropRect(0, 800, 2540, 3200), area.rect)
        assertEquals(CropRect(1460, 0, 2540, 2400), area.framing.crop)
        assertEquals(CropRect(0, 1200, 2400, 2280), area.framing.turned)
    }

    @Test
    fun `a turned picture is exported upright about the crop, which fills it unturned`() {
        val tilted = portrait.copy(rotation = 10f)
        val landscape = CropRect(left = 800, top = 1460, right = 3200, bottom = 2540, rotation = 25f)

        val area = ExportArea.of(Framing(tilted, landscape), FRAME, FRAME)

        assertEquals(10f, area.rect.rotation)
        val crop = area.framing.crop
        assertEquals(0f, crop.rotation)
        assertEquals(portrait.width, crop.width)
        assertEquals(portrait.height, crop.height)
        assertEquals(area.width / 2f, crop.centerX, 1f)
        val turned = checkNotNull(area.framing.turned)
        assertEquals(15f, turned.rotation)
        assertEquals(area.width / 2f, turned.centerX, 1f)
        assertEquals(area.height / 2f, turned.centerY, 1f)
        // The turned window's widest extent fits the export.
        assertTrue(area.width >= 2400 && area.height >= 2400)
    }

    @Test
    fun `crop only exports the crop as before, all of it the window`() {
        val area = ExportArea.cropOnly(Framing(portrait, CropRect(0, 0, 2400, 1080)))

        assertEquals(portrait, area.rect)
        assertEquals(Framing(CropRect(0, 0, 1080, 2400)), area.framing)
    }

    private companion object {
        const val FRAME = 4000
    }
}
