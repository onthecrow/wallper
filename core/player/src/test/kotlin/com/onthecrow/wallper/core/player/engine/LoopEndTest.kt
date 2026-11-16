package com.onthecrow.wallper.core.player.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoopEndTest {

    @Test
    fun `a clip that ends after the video ends with the video`() {
        // The clip of a file whose sound track (5119 ms) outlasts its video (5000 ms), saved to the end.
        assertEquals(5_000_000, loopEndUs(clipStartMs = 0, clipEndMs = 5_119, videoEndUs = 5_000_000))
    }

    @Test
    fun `a clip that ends before the video keeps its end`() {
        assertEquals(3_000_000, loopEndUs(clipStartMs = 500, clipEndMs = 3_000, videoEndUs = 5_000_000))
    }

    @Test
    fun `a clip that ends with the video keeps its end`() {
        assertEquals(5_000_000, loopEndUs(clipStartMs = 0, clipEndMs = 5_000, videoEndUs = 5_000_000))
    }

    @Test
    fun `a clip to the end of the file ends with the video`() {
        assertEquals(5_000_000, loopEndUs(clipStartMs = 0, clipEndMs = null, videoEndUs = 5_000_000))
        assertEquals(5_000_000, loopEndUs(clipStartMs = null, clipEndMs = null, videoEndUs = 5_000_000))
    }

    @Test
    fun `the end of the video is kept to the microsecond`() {
        assertEquals(4_999_990, loopEndUs(clipStartMs = 0, clipEndMs = 5_000, videoEndUs = 4_999_990))
    }

    @Test
    fun `without the end of the video the clip is left as it is`() {
        assertEquals(5_119_000, loopEndUs(clipStartMs = 0, clipEndMs = 5_119, videoEndUs = null))
        assertNull(loopEndUs(clipStartMs = 1_000, clipEndMs = null, videoEndUs = null))
    }

    @Test
    fun `a video that is over before the clip starts leaves the clip as it is`() {
        assertEquals(8_000_000, loopEndUs(clipStartMs = 6_000, clipEndMs = 8_000, videoEndUs = 5_000_000))
        assertEquals(8_000_000, loopEndUs(clipStartMs = 5_000, clipEndMs = 8_000, videoEndUs = 5_000_000))
        assertNull(loopEndUs(clipStartMs = 6_000, clipEndMs = null, videoEndUs = 5_000_000))
    }
}
