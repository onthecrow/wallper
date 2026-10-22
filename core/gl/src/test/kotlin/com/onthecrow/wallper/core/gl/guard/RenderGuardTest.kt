package com.onthecrow.wallper.core.gl.guard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderGuardTest {

    private var nowMs = 1_000L
    private val repairs = FakeRepairs()
    private val unrecoverable = mutableListOf<RenderStall>()
    private val guard = RenderGuard(repairs, { unrecoverable += it }, { nowMs }).apply { setExpected(true) }

    private fun runFor(ms: Long, stepMs: Long = 50) {
        var left = ms
        while (left > 0) {
            nowMs += stepMs
            left -= stepMs
            guard.check()
        }
    }

    @Test
    fun `missing frames are nudged a few times and never escalated`() {
        runFor(5_000)

        assertEquals(listOf("redraw", "redraw", "redraw"), repairs.actions)
        assertTrue(unrecoverable.isEmpty())
    }

    @Test
    fun `a presented frame ends the nudging`() {
        runFor(200)
        guard.onFramePresented()
        runFor(100)

        assertEquals(listOf("redraw"), repairs.actions)
    }

    @Test
    fun `bad surface recreates the surface, then the context, then gives up`() {
        guard.onSwapFailed(0x300D)
        runFor(3_000)

        assertEquals(listOf("surface", "context"), repairs.actions)
        assertEquals(listOf<RenderStall>(RenderStall.SurfaceLost), unrecoverable)
    }

    @Test
    fun `lost context goes straight to a new context and repeated errors do not restart it`() {
        guard.onSwapFailed(0x300E)
        guard.onSwapFailed(0x300E)
        guard.onDrawFailed()
        guard.onFramePresented()

        assertEquals(listOf("context"), repairs.actions)
        assertTrue(unrecoverable.isEmpty())
    }

    @Test
    fun `a worse failure replaces the nudging`() {
        runFor(200)
        guard.onSwapFailed(0x300E)

        assertEquals(listOf("redraw", "context"), repairs.actions)
    }

    @Test
    fun `nothing happens while frames are not expected`() {
        guard.setExpected(false)
        guard.onSwapFailed(0x300E)
        runFor(5_000)

        assertTrue(repairs.actions.isEmpty())
        assertEquals(Long.MAX_VALUE, guard.nextCheckInMs())
    }

    @Test
    fun `restarts are limited per window`() {
        var now = 0L
        val policy = GlRestartPolicy({ now })

        assertTrue(List(3) { policy.tryAcquire() }.all { it })
        assertFalse(policy.tryAcquire())

        now += 120_001
        assertTrue(policy.tryAcquire())
    }

    private class FakeRepairs : RenderRepairs {
        val actions = mutableListOf<String>()
        override fun redraw() { actions += "redraw" }
        override fun recreateSurface() { actions += "surface" }
        override fun recreateContext() { actions += "context" }
    }
}
