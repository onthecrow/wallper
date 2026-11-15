package com.onthecrow.wallper.core.engine.impl.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoFramePacerTest {

    private var vsyncAwaited = false
    private var draws = 0

    /** The most frames drawn between two vsyncs: more than one fills the window's buffer queue. */
    private var mostDrawsInARefresh = 0
    private var drawsThisRefresh = 0

    private val pacer = VideoFramePacer(
        awaitVsync = {
            assertFalse(vsyncAwaited, "a vsync is awaited once")
            vsyncAwaited = true
        },
        draw = {
            draws++
            mostDrawsInARefresh = maxOf(mostDrawsInARefresh, ++drawsThisRefresh)
        },
    )

    /** The next vsync, if anything waits for it; false when the pacer sleeps. */
    private fun vsync(): Boolean {
        drawsThisRefresh = 0
        if (!vsyncAwaited) return false
        vsyncAwaited = false
        pacer.onVsync()
        return true
    }

    @Test
    fun `a video frame is drawn at the next vsync`() {
        pacer.onVideoFrame()
        assertEquals(0, draws)

        vsync()

        assertEquals(1, draws)
    }

    @Test
    fun `video frames between two vsyncs make one frame`() {
        pacer.onVideoFrame()
        pacer.onVideoFrame()

        vsync()

        assertEquals(1, draws)
    }

    @Test
    fun `nothing runs while nothing asks`() {
        pacer.onVideoFrame()
        vsync()

        assertFalse(vsync())
    }

    @Test
    fun `an offset is drawn as it comes`() {
        pacer.onOffset()

        assertEquals(1, draws)
    }

    @Test
    fun `a swipe draws each offset at once, one frame a refresh`() {
        repeat(10) {
            vsync()
            pacer.onOffset()
        }

        assertEquals(10, draws)
        assertEquals(1, mostDrawsInARefresh)
    }

    @Test
    fun `during a swipe a video frame is drawn by the next offset, not at the vsync`() {
        vsync()
        pacer.onOffset()
        pacer.onVideoFrame()

        vsync()
        assertEquals(1, draws) // the vsync left it to the offset about to come

        pacer.onOffset()
        assertEquals(2, draws)

        vsync()
        assertEquals(2, draws) // and nothing is left of it
        assertEquals(1, mostDrawsInARefresh)
    }

    @Test
    fun `a video frame that waits when the swipe ends is drawn a vsync later`() {
        vsync()
        pacer.onOffset()
        pacer.onVideoFrame()

        vsync() // no offset follows this one
        assertEquals(1, draws)

        vsync()

        assertEquals(2, draws)
        assertFalse(vsync())
    }

    @Test
    fun `the offset that starts a swipe waits when the refresh has a video frame's draw`() {
        pacer.onVideoFrame()
        vsync()
        assertEquals(1, draws)

        pacer.onOffset()
        assertEquals(1, draws)

        vsync()
        pacer.onOffset() // the swipe goes on: this one draws, the first is behind it
        assertEquals(2, draws)
        assertEquals(1, mostDrawsInARefresh)
    }

    @Test
    fun `an offset that waited is drawn when no other follows`() {
        pacer.onVideoFrame()
        vsync()
        pacer.onOffset() // the only one: a launcher that set its page without a swipe

        vsync()
        vsync()

        assertEquals(2, draws)
        assertFalse(vsync())
    }

    @Test
    fun `a second offset within a refresh waits for the next`() {
        vsync()
        pacer.onOffset()
        pacer.onOffset() // the launcher was late with the one before

        assertEquals(1, draws)

        vsync()
        pacer.onOffset()

        assertEquals(2, draws)
        assertEquals(1, mostDrawsInARefresh)
    }

    @Test
    fun `a paused video's swipe draws without any video frames`() {
        repeat(3) {
            pacer.onOffset()
            assertTrue(vsync())
        }

        assertEquals(3, draws)
        assertFalse(vsync())
    }

    @Test
    fun `a swipe with video frames never draws twice in a refresh`() {
        pacer.onVideoFrame()
        repeat(40) { refresh ->
            vsync()
            // The offset just after the vsync; a video frame every fourth refresh, at any point of it.
            if (refresh % 4 == 1) pacer.onVideoFrame()
            pacer.onOffset()
            if (refresh % 4 == 3) pacer.onVideoFrame()
        }
        while (vsync()) Unit

        assertEquals(1, mostDrawsInARefresh)
    }
}
