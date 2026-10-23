package com.onthecrow.wallper.editor.preview

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoFramesTest {

    // 48 dp at 3.5 px per dp.
    private val heightPx = 168

    @Test
    fun `a short video gets a frame for every tile`() {
        // A portrait tile is 27 dp wide: 422 ms of video at 64 dp a second.
        assertEquals(422, stripFrameIntervalMs(durationMs = 16_000, heightPx = heightPx, aspectRatio = 9f / 16f))
        assertEquals(422, stripFrameIntervalMs(durationMs = 0, heightPx = heightPx, aspectRatio = 9f / 16f))
    }

    @Test
    fun `a long video spreads its frames to stay within the budget`() {
        // 125 portrait frames fit: a minute needs 480 ms per frame.
        assertEquals(480, stripFrameIntervalMs(durationMs = 60_000, heightPx = heightPx, aspectRatio = 9f / 16f))
        // Landscape frames are wider, so only 39 fit.
        assertEquals(1_539, stripFrameIntervalMs(durationMs = 60_000, heightPx = heightPx, aspectRatio = 16f / 9f))
    }
}
