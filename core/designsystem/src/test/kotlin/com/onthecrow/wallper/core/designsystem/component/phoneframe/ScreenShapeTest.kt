package com.onthecrow.wallper.core.designsystem.component.phoneframe

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenShapeTest {

    private val frameScreen = Size(300f, 660f)

    @Test
    fun `the device's corners come at the frame's scale`() {
        // Corners of 108 pixels on a screen 1080 wide.
        assertEquals(30f, ScreenShape(cornerRadius = 108f / 1080).cornerRadiusOn(frameScreen, fallback = 7f), 1e-3f)
    }

    @Test
    fun `unknown corners leave them to the frame`() {
        assertEquals(7f, ScreenShape.Unknown.cornerRadiusOn(frameScreen, fallback = 7f))
    }
}
