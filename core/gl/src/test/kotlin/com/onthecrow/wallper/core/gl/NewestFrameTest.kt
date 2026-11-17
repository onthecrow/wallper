package com.onthecrow.wallper.core.gl

import kotlin.test.Test
import kotlin.test.assertEquals

class NewestFrameTest {

    /** A SurfaceTexture's queue: a latch takes the oldest frame waiting, and keeps the one it has when none does. */
    private class FakeTexture(private var current: Long = 0, vararg queued: Long) {
        private val queue = ArrayDeque(queued.toList())
        var latches = 0
            private set
        val waiting: Int get() = queue.size

        fun queue(timestampNs: Long) = queue.addLast(timestampNs)

        fun latchNext(): Long {
            latches++
            queue.removeFirstOrNull()?.let { current = it }
            return current
        }
    }

    @Test
    fun `an empty queue keeps the frame and costs one latch`() {
        val texture = FakeTexture(current = 100)

        assertEquals(100, NewestFrame.latch(previousNs = 100, texture::latchNext))
        assertEquals(1, texture.latches)
    }

    @Test
    fun `the first frame is latched`() {
        val texture = FakeTexture(current = 0, 100)

        assertEquals(100, NewestFrame.latch(previousNs = 0, texture::latchNext))
    }

    @Test
    fun `frames that waited are passed over for the newest`() {
        val texture = FakeTexture(current = 100, 200, 300, 400)

        assertEquals(400, NewestFrame.latch(previousNs = 100, texture::latchNext))
        assertEquals(0, texture.waiting)
    }

    @Test
    fun `nothing waits after a draw, so the next frame is shown by the draw it asks for`() {
        val texture = FakeTexture(current = 100, 200, 300)
        NewestFrame.latch(previousNs = 100, texture::latchNext)

        texture.queue(400)

        assertEquals(400, NewestFrame.latch(previousNs = 300, texture::latchNext))
    }

    @Test
    fun `a queue that never empties ends the draw all the same`() {
        var timestampNs = 0L

        val latched = NewestFrame.latch(previousNs = 0) { ++timestampNs }

        assertEquals(NewestFrame.MAX_LATCHES.toLong(), latched)
    }
}
