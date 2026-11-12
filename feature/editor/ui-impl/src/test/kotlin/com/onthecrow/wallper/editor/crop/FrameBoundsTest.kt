package com.onthecrow.wallper.editor.crop

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class FrameBoundsTest {

    private val padding = Rect(left = 10f, top = 20f, right = 10f, bottom = 20f)

    @Test
    fun `a tall screen takes the zone's whole height, centred across`() {
        val bounds = FrameBounds.fit(Size(400f, 540f), padding, border = 5f, aspectRatio = 0.5f)

        assertEquals(Rect(left = 72.5f, top = 20f, right = 327.5f, bottom = 520f), bounds.body)
        assertEquals(Rect(left = 77.5f, top = 25f, right = 322.5f, bottom = 515f), bounds.screen)
        assertEquals(0.5f, bounds.screen.width / bounds.screen.height, 1e-6f)
    }

    @Test
    fun `a wide screen takes the zone's whole width, centred up and down`() {
        val bounds = FrameBounds.fit(Size(400f, 540f), padding, border = 5f, aspectRatio = 1.6f)

        assertEquals(10f, bounds.body.left)
        assertEquals(390f, bounds.body.right)
        assertEquals(1.6f, bounds.screen.width / bounds.screen.height, 1e-6f)
        assertEquals(270f, bounds.body.center.y, 1e-4f)
    }

    @Test
    fun `a zone smaller than the border leaves no screen`() {
        val bounds = FrameBounds.fit(Size(25f, 45f), padding, border = 5f, aspectRatio = 0.5f)

        assertEquals(0f, bounds.screen.width)
        assertEquals(0f, bounds.screen.height)
    }
}
