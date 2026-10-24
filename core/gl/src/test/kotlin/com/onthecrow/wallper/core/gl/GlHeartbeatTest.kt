package com.onthecrow.wallper.core.gl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlHeartbeatTest {

    @Test
    fun `nothing is hung while no frames are expected`() {
        assertFalse(GlHeartbeat.isHung(nowMs = 60_000, armedSinceMs = null, lastLoopAtMs = 0, slowFrame = false))
    }

    @Test
    fun `a silent thread is hung after the timeout`() {
        assertFalse(GlHeartbeat.isHung(nowMs = 10_000, armedSinceMs = 5_000, lastLoopAtMs = 8_500, slowFrame = false))
        assertTrue(GlHeartbeat.isHung(nowMs = 10_000, armedSinceMs = 5_000, lastLoopAtMs = 7_900, slowFrame = false))
    }

    @Test
    fun `silence counts from when frames became expected`() {
        // The thread slept through a still frame: that was no hang.
        assertFalse(GlHeartbeat.isHung(nowMs = 10_000, armedSinceMs = 9_000, lastLoopAtMs = 0, slowFrame = false))
    }

    @Test
    fun `a frame compiling shaders gets more time`() {
        assertFalse(GlHeartbeat.isHung(nowMs = 10_000, armedSinceMs = 0, lastLoopAtMs = 2_000, slowFrame = true))
        assertTrue(GlHeartbeat.isHung(nowMs = 13_000, armedSinceMs = 0, lastLoopAtMs = 2_000, slowFrame = true))
    }

    @Test
    fun `the heartbeat beats only while frames are expected`() = runTest {
        val framesExpected = MutableStateFlow(false)
        var checks = 0
        val heartbeat = launch { GlHeartbeat.run(framesExpected) { checks++ } }

        // A hidden wallpaper: not a single wake-up.
        advanceTimeBy(60_000)
        assertEquals(0, checks)

        framesExpected.value = true
        advanceTimeBy(4 * GlHeartbeat.CHECK_INTERVAL_MS)
        runCurrent()
        assertEquals(4, checks)

        framesExpected.value = false
        advanceTimeBy(60_000)
        assertEquals(4, checks)
        heartbeat.cancel()
    }
}
