package com.onthecrow.wallper.core.player.supervisor

import androidx.media3.common.PlaybackException
import com.onthecrow.wallper.core.player.StallCause
import com.onthecrow.wallper.core.player.engine.EngineSnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallWatchdogTest {

    private val engine = FakeEngine(id = 1, startPositionMs = 0)

    private fun TestScope.watch() = async { StallWatchdog { testScheduler.currentTime }.watch(engine) }

    @Test
    fun `steady frames keep it watching`() = runTest {
        val watch = watch()

        advanceTimeBy(30_000)

        assertTrue(watch.isActive)
        watch.cancel()
    }

    @Test
    fun `a stalled decoder is reported after five frame intervals and nothing is touched`() = runTest {
        val watch = watch()
        advanceTimeBy(2_000)
        engine.healthy = false

        advanceTimeBy(100)
        assertTrue(watch.isActive)
        advanceTimeBy(1_000)

        assertEquals(StallCause.DecoderStalled, watch.await())
        assertTrue(engine.actions.isEmpty())
    }

    @Test
    fun `no first frame is reported after its own timeout`() = runTest {
        engine.healthy = false
        val watch = watch()

        advanceTimeBy(1_900)
        assertTrue(watch.isActive)
        advanceTimeBy(600)

        assertEquals(StallCause.NoFirstFrame, watch.await())
    }

    @Test
    fun `stuck at the clip end is reported as such`() = runTest {
        val watch = watch()
        advanceTimeBy(2_000)
        engine.healthy = false
        engine.positionMs = engine.durationMs

        advanceTimeBy(1_000)

        assertEquals(StallCause.StuckAtClipEnd, watch.await())
    }

    @Test
    fun `stuck buffering is reported after its own timeout`() = runTest {
        val watch = watch()
        advanceTimeBy(2_000)
        engine.healthy = false
        engine.state = EngineSnapshot.State.BUFFERING

        advanceTimeBy(1_250)
        assertTrue(watch.isActive)
        advanceTimeBy(500)

        assertEquals(StallCause.StuckBuffering, watch.await())
    }

    @Test
    fun `a loop boundary gives the decoder extra time`() = runTest {
        val watch = watch()
        advanceTimeBy(2_000)
        engine.healthy = false
        engine.loopBoundaries.tryEmit(Unit)

        advanceTimeBy(1_250)

        assertTrue(watch.isActive)
        watch.cancel()
    }

    @Test
    fun `an error ends the watch right away`() = runTest {
        val watch = watch()
        advanceTimeBy(1_000)

        engine.error.value = PlaybackException.ERROR_CODE_DECODING_FAILED
        runCurrent()

        assertFalse(watch.isActive)
        assertEquals(StallCause.Failed(PlaybackException.ERROR_CODE_DECODING_FAILED), watch.await())
    }
}
