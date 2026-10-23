package com.onthecrow.wallper.editor.preview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewGuardTest {

    private val watchdog = PreviewWatchdog(stallMs = 3_000)

    @Test
    fun `a player that should move on and stands still for the whole wait has stopped`() {
        watchdog.onSample(nowMs = 0, expectsProgress = true, frames = 10, positionMs = 2_000)

        assertFalse(watchdog.onSample(nowMs = 2_999, expectsProgress = true, frames = 10, positionMs = 2_000))
        assertTrue(watchdog.onSample(nowMs = 3_000, expectsProgress = true, frames = 10, positionMs = 2_000))
    }

    @Test
    fun `a new frame or a new position starts the wait over`() {
        watchdog.onSample(nowMs = 0, expectsProgress = true, frames = 10, positionMs = 2_000)
        watchdog.onSample(nowMs = 2_500, expectsProgress = true, frames = 11, positionMs = 2_000)
        watchdog.onSample(nowMs = 5_000, expectsProgress = true, frames = 11, positionMs = 2_400)

        assertFalse(watchdog.onSample(nowMs = 7_000, expectsProgress = true, frames = 11, positionMs = 2_400))
        assertTrue(watchdog.onSample(nowMs = 8_000, expectsProgress = true, frames = 11, positionMs = 2_400))
    }

    @Test
    fun `a paused player shown still is fine however long`() {
        watchdog.onSample(nowMs = 0, expectsProgress = false, frames = 10, positionMs = 2_000)

        assertFalse(watchdog.onSample(nowMs = 60_000, expectsProgress = false, frames = 10, positionMs = 2_000))
        // A seek after that gets the whole wait.
        assertFalse(watchdog.onSample(nowMs = 62_000, expectsProgress = true, frames = 10, positionMs = 2_000))
    }

    @Test
    fun `a new player gets the whole wait`() {
        watchdog.onSample(nowMs = 0, expectsProgress = true, frames = 10, positionMs = 2_000)
        watchdog.reset(nowMs = 3_000)

        assertFalse(watchdog.onSample(nowMs = 5_000, expectsProgress = true, frames = 10, positionMs = 2_000))
        assertTrue(watchdog.onSample(nowMs = 6_000, expectsProgress = true, frames = 10, positionMs = 2_000))
    }

    @Test
    fun `at most three new players a minute`() {
        val policy = RebuildPolicy(max = 3, windowMs = 60_000)

        assertTrue(policy.tryAcquire(nowMs = 0))
        assertTrue(policy.tryAcquire(nowMs = 10_000))
        assertTrue(policy.tryAcquire(nowMs = 20_000))
        assertFalse(policy.tryAcquire(nowMs = 30_000))
        // A minute after the first one, one more.
        assertTrue(policy.tryAcquire(nowMs = 60_000))
        assertFalse(policy.tryAcquire(nowMs = 65_000))
    }
}
