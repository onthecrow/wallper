package com.onthecrow.wallper.core.player.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeadInTest {

    /** A clip from 1 s to 5 s of a 30 fps file whose only key frame is the first one. */
    private val leadIn = LeadIn(clipStartUs = CLIP_START_US)

    @Test
    fun `a frame before the clip's start is in the lead-in`() {
        assertTrue(leadIn.contains(timeUs = 0, streamOffsetUs = 0))
        assertTrue(leadIn.contains(timeUs = CLIP_START_US - 1, streamOffsetUs = 0))
    }

    @Test
    fun `the clip's first frame and the ones after it are not`() {
        assertFalse(leadIn.contains(timeUs = CLIP_START_US, streamOffsetUs = 0))
        assertFalse(leadIn.contains(timeUs = CLIP_END_US - FRAME_US, streamOffsetUs = 0))
    }

    @Test
    fun `a stream's offset is taken off its frames' times`() {
        assertTrue(leadIn.contains(timeUs = OFFSET_US + CLIP_START_US - 1, streamOffsetUs = OFFSET_US))
        assertFalse(leadIn.contains(timeUs = OFFSET_US + CLIP_START_US, streamOffsetUs = OFFSET_US))
    }

    @Test
    fun `the lead-in of the first stream keeps its times`() {
        leadIn.onStreamChanged()
        leadIn.onPositionReset() // a renderer is enabled with a stream and then a position

        assertEquals(OFFSET_US, queue(OFFSET_US, OFFSET_US))
        assertEquals(OFFSET_US + FRAME_US, queue(OFFSET_US + FRAME_US, OFFSET_US))
    }

    @Test
    fun `the lead-in of a loop goes on from the last frame of the loop before`() {
        val lastFrameUs = playFirstLoop()
        val nextOffsetUs = nextLoop()

        // On its own the lead-in would start a clip's length before the frames that were just queued.
        assertEquals(lastFrameUs + 1, queue(nextOffsetUs, nextOffsetUs))
        assertEquals(lastFrameUs + 2, queue(nextOffsetUs + FRAME_US, nextOffsetUs))
        assertEquals(lastFrameUs + 3, queue(nextOffsetUs + 2 * FRAME_US, nextOffsetUs))
    }

    @Test
    fun `a loop's lead-in stays in the lead-in with its new times`() {
        playFirstLoop()
        val nextOffsetUs = nextLoop()

        repeat(30) { frame ->
            val queuedUs = queue(nextOffsetUs + frame * FRAME_US, nextOffsetUs)
            assertTrue(leadIn.contains(queuedUs, nextOffsetUs))
        }
    }

    @Test
    fun `the clip's own frames keep their times in every loop`() {
        playFirstLoop()
        val nextOffsetUs = nextLoop()
        queue(nextOffsetUs, nextOffsetUs)

        val clipStartUs = nextOffsetUs + CLIP_START_US
        assertEquals(clipStartUs, queue(clipStartUs, nextOffsetUs))
        assertEquals(clipStartUs + FRAME_US, queue(clipStartUs + FRAME_US, nextOffsetUs))
    }

    @Test
    fun `a frame of the clip that is queued among the lead-in keeps its time`() {
        playFirstLoop()
        val nextOffsetUs = nextLoop()
        val clipStartUs = nextOffsetUs + CLIP_START_US

        // Frames that others are predicted from come first: one of the clip before the lead-in's last ones.
        val before = queue(clipStartUs - 3 * FRAME_US, nextOffsetUs)
        assertEquals(clipStartUs + FRAME_US, queue(clipStartUs + FRAME_US, nextOffsetUs))
        assertEquals(before + 1, queue(clipStartUs - 2 * FRAME_US, nextOffsetUs))
        assertEquals(before + 2, queue(clipStartUs - FRAME_US, nextOffsetUs))
    }

    @Test
    fun `a lead-in longer than the room it has never reaches the clip's start`() {
        // The last loop's latest frame is 3 microseconds before its end: room for two times.
        val lastFrameUs = OFFSET_US + CLIP_END_US - 3
        leadIn.onPositionReset()
        leadIn.onQueued(lastFrameUs)
        val nextOffsetUs = nextLoop()
        val clipStartUs = nextOffsetUs + CLIP_START_US

        assertEquals(clipStartUs - 2, queue(nextOffsetUs, nextOffsetUs))
        assertEquals(clipStartUs - 1, queue(nextOffsetUs + FRAME_US, nextOffsetUs))
        assertEquals(clipStartUs - 1, queue(nextOffsetUs + 2 * FRAME_US, nextOffsetUs))
        assertEquals(clipStartUs, queue(clipStartUs, nextOffsetUs))
    }

    @Test
    fun `every loop's lead-in goes on from the loop before it`() {
        playFirstLoop()
        val secondOffsetUs = nextLoop()
        queue(secondOffsetUs, secondOffsetUs)
        val lastFrameUs = secondOffsetUs + CLIP_END_US - FRAME_US
        queue(lastFrameUs, secondOffsetUs)

        leadIn.onStreamChanged()
        val thirdOffsetUs = secondOffsetUs + CLIP_END_US - CLIP_START_US
        assertEquals(lastFrameUs + 1, queue(thirdOffsetUs, thirdOffsetUs))
    }

    @Test
    fun `after a flush the lead-in keeps its times again`() {
        playFirstLoop()
        val nextOffsetUs = nextLoop()
        queue(nextOffsetUs, nextOffsetUs)

        leadIn.onPositionReset() // a seek to the clip's start

        assertEquals(nextOffsetUs, queue(nextOffsetUs, nextOffsetUs))
        assertEquals(nextOffsetUs + FRAME_US, queue(nextOffsetUs + FRAME_US, nextOffsetUs))
    }

    @Test
    fun `a stream that follows one with nothing queued keeps its times`() {
        leadIn.onPositionReset()
        leadIn.onStreamChanged()

        assertEquals(OFFSET_US, queue(OFFSET_US, OFFSET_US))
    }

    /** Queues the frame as the renderer does; returns the time it was queued with. */
    private fun queue(timeUs: Long, streamOffsetUs: Long): Long =
        leadIn.queueTimeUs(timeUs, streamOffsetUs).also(leadIn::onQueued)

    /** The first stream after the renderer was enabled, up to the clip's last frame; returns its time. */
    private fun playFirstLoop(): Long {
        leadIn.onStreamChanged()
        leadIn.onPositionReset()
        queue(OFFSET_US, OFFSET_US)
        return queue(OFFSET_US + CLIP_END_US - FRAME_US, OFFSET_US)
    }

    /** The stream of the next loop takes over; returns its offset, as Media3 works it out. */
    private fun nextLoop(): Long {
        leadIn.onStreamChanged()
        return OFFSET_US + CLIP_END_US - CLIP_START_US
    }

    private companion object {
        const val CLIP_START_US = 1_000_000L
        const val CLIP_END_US = 5_000_000L
        const val FRAME_US = 33_333L

        /** Where Media3 puts the first stream in the renderer's time. */
        const val OFFSET_US = 1_000_000_000_000L
    }
}
