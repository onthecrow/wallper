package com.onthecrow.wallper.core.player.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class ClipCutTest {

    @Test
    fun `an end on a frame stays`() {
        val times = frames(0, 1, 2, 3, 4, 5, 6)

        assertEquals(at(4.0), cutEndUs(startUs = 0, endUs = at(4.0), timesUs = times))
    }

    @Test
    fun `an end between two frames moves to the frame that isn't shown`() {
        val times = frames(0, 1, 2, 3, 4, 5, 6)

        // Frame 4 is the last one shown, and it stays until frame 5 would have come.
        assertEquals(at(5.0), cutEndUs(startUs = 0, endUs = at(4.5), timesUs = times))
        assertEquals(at(5.0), cutEndUs(startUs = 0, endUs = at(4.0) + 1, timesUs = times))
    }

    @Test
    fun `an end before B-frames that are cut off moves to the first of them`() {
        // Two B-frames between the frames they are predicted from, which come first in the file.
        val times = frames(0, 3, 1, 2, 6, 4, 5, 9, 7, 8)

        // Frame 6 is the first at or after the end: 4 and 5 follow it in the file and go with it.
        assertEquals(at(4.0), cutEndUs(startUs = 0, endUs = at(5.5), timesUs = times))
        assertEquals(at(4.0), cutEndUs(startUs = 0, endUs = at(6.0), timesUs = times))
        assertEquals(at(4.0), cutEndUs(startUs = 0, endUs = at(4.5), timesUs = times))
    }

    @Test
    fun `an end that cuts no B-frame off moves like one in a video without them`() {
        val times = frames(0, 3, 1, 2, 6, 4, 5, 9, 7, 8)

        // Everything before frame 6 in the file is shown, up to frame 3; frame 4 is the first that isn't.
        assertEquals(at(4.0), cutEndUs(startUs = 0, endUs = at(3.5), timesUs = times))
        assertEquals(at(4.0), cutEndUs(startUs = 0, endUs = at(4.0), timesUs = times))
        // Frame 9 is the first at or after the end; 7 and 8 follow it, and 7 is the first not shown.
        assertEquals(at(7.0), cutEndUs(startUs = 0, endUs = at(6.5), timesUs = times))
    }

    @Test
    fun `B-frames predicted from B-frames are cut off together`() {
        val times = frames(0, 4, 2, 1, 3, 8, 6, 5, 7)

        assertEquals(at(5.0), cutEndUs(startUs = 0, endUs = at(7.5), timesUs = times))
    }

    @Test
    fun `an end after the last frame stays`() {
        val times = frames(0, 3, 1, 2, 6, 4, 5)

        assertEquals(at(6.5), cutEndUs(startUs = 0, endUs = at(6.5), timesUs = times))
        assertEquals(at(60.0), cutEndUs(startUs = 0, endUs = at(60.0), timesUs = times))
    }

    @Test
    fun `an end that moved is checked again`() {
        // At 5.5 frame 6 cuts off 2, 3 and 5; at 2 frame 4 cuts off 1.
        val times = frames(0, 4, 1, 6, 2, 3, 5)

        assertEquals(at(1.0), cutEndUs(startUs = 0, endUs = at(5.5), timesUs = times))
    }

    @Test
    fun `a clip that would be left without frames keeps its end`() {
        val times = frames(0, 3, 1, 2, 6, 4, 5, 9, 7, 8)

        // From 4 to 5.5 only frames 4 and 5 are in the clip, and both are cut off.
        assertEquals(at(5.5), cutEndUs(startUs = at(4.0), endUs = at(5.5), timesUs = times))
        assertEquals(at(5.5), cutEndUs(startUs = at(4.5), endUs = at(5.5), timesUs = times))
        assertEquals(at(4.0), cutEndUs(startUs = at(3.0), endUs = at(5.5), timesUs = times))
    }

    @Test
    fun `no frames leave the end as it is`() {
        assertEquals(at(5.5), cutEndUs(startUs = 0, endUs = at(5.5), timesUs = emptyList()))
    }

    /** Frames in decoding order by their number in the order they are shown, 30 a second. */
    private fun frames(vararg numbers: Int): List<Long> = numbers.map { at(it.toDouble()) }

    private fun at(frame: Double): Long = (frame * FRAME_US).toLong()

    private companion object {
        const val FRAME_US = 33_333L
    }
}
