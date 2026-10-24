package com.onthecrow.wallper.core.player.supervisor

import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.media3.common.PlaybackException
import com.onthecrow.wallper.core.player.FailureCause
import com.onthecrow.wallper.core.player.StallCause
import com.onthecrow.wallper.core.player.WallperPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EngineSupervisorTest {

    private val screen = newSurface()
    private val playWhenReady = MutableStateFlow(true)

    private fun TestScope.start(factory: FakeEngineFactory): EngineSupervisor {
        val supervisor = EngineSupervisor(factory, clock = { testScheduler.currentTime })
        supervisor.setSurface(screen)
        backgroundScope.launch { supervisor.run(playWhenReady) }
        runCurrent()
        return supervisor
    }

    @Test
    fun `both engines are created right away, before play`() = runTest {
        playWhenReady.value = false
        val factory = FakeEngineFactory()
        val supervisor = start(factory)

        val (active, standby) = factory.created
        assertSame(screen, active.createdOnSurface)
        assertFalse(active.playing)
        assertNull(standby.createdOnSurface)
        assertFalse(standby.playing)
        assertEquals(WallperPlayerState.Idle, supervisor.state.value)
    }

    @Test
    fun `play starts the active engine only`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)

        val (active, standby) = factory.created
        assertTrue(active.playing)
        assertFalse(standby.playing)
        assertEquals(WallperPlayerState.Playing, supervisor.state.value)
    }

    @Test
    fun `a stall switches to the standby where the active one was and replaces the stalled one`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)
        val (stalled, standby) = factory.created
        advanceTimeBy(2_000)

        stalled.healthy = false
        advanceTimeBy(1_000)

        val stalledAtMs = stalled.positionMs
        // The old decoder lets go of the surface before the standby takes it.
        assertEquals(
            listOf("#1 play", "#1 release", "#2 setSurface", "#2 seekTo $stalledAtMs", "#2 play"),
            factory.journal,
        )
        assertSame(screen, standby.surface)
        val replacement = factory.created[2]
        assertNull(replacement.createdOnSurface)
        assertFalse(replacement.playing)
        assertEquals(WallperPlayerState.Playing, supervisor.state.value)
    }

    @Test
    fun `stuck at the clip end switches to the start of the clip`() = runTest {
        val factory = FakeEngineFactory()
        start(factory)
        val (stalled, standby) = factory.created
        advanceTimeBy(2_000)

        stalled.healthy = false
        stalled.positionMs = stalled.durationMs
        advanceTimeBy(1_000)

        assertTrue("#2 seekTo 0" in factory.journal)
        assertTrue(standby.playing)
    }

    @Test
    fun `a stall while the standby is still preparing waits for it`() = runTest {
        val factory = FakeEngineFactory { index -> if (index == 2) isReady.value = false }
        val supervisor = start(factory)
        val (stalled, standby) = factory.created
        advanceTimeBy(600)

        stalled.healthy = false
        advanceTimeBy(1_000)
        assertIs<WallperPlayerState.Recovering>(supervisor.state.value)
        assertFalse(stalled.released)

        standby.isReady.value = true
        runCurrent()

        assertTrue(stalled.released)
        assertTrue(standby.playing)
    }

    @Test
    fun `a standby that failed while waiting is left alone until a switch needs it, then recreated`() = runTest {
        val factory = FakeEngineFactory()
        start(factory)
        val (stalled, broken) = factory.created
        advanceTimeBy(2_000)

        broken.error.value = PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
        advanceTimeBy(1_000)
        assertEquals(2, factory.created.size)
        assertFalse(broken.released)

        stalled.healthy = false
        advanceTimeBy(3_000)

        assertTrue(broken.released)
        val (recreated, nextStandby) = factory.created.drop(2)
        assertTrue(recreated.playing)
        assertSame(screen, recreated.surface)
        assertFalse(nextStandby.playing)
    }

    @Test
    fun `a broken file fails at once`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)

        factory.created[0].error.value = PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        advanceTimeBy(30_000)

        assertEquals(2, factory.created.size)
        assertTrue(factory.created.all { it.released })
        assertEquals(
            WallperPlayerState.Failed(FailureCause.ContentBroken(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)),
            supervisor.state.value,
        )
    }

    @Test
    fun `a file the device can't play gives up once the budget is spent`() = runTest {
        val factory = FakeEngineFactory { healthy = false }
        val supervisor = start(factory)

        advanceTimeBy(60_000)

        // The first pair, then one replacement per granted attempt.
        assertEquals(6, factory.created.size)
        assertTrue(factory.created.all { it.released })
        val state = supervisor.state.value
        assertIs<WallperPlayerState.Failed>(state)
        assertEquals(FailureCause.RetriesExhausted(StallCause.NoFirstFrame), state.cause)
    }

    @Test
    fun `paused for long releases both engines and play creates them again where it was`() = runTest {
        val factory = FakeEngineFactory()
        start(factory)
        advanceTimeBy(2_000)
        val (first, standby) = factory.created

        playWhenReady.value = false
        advanceTimeBy(15_001)
        assertTrue(first.released)
        assertTrue(standby.released)

        playWhenReady.value = true
        runCurrent()

        val (active, nextStandby) = factory.created.drop(2)
        assertEquals(first.positionMs, active.startPositionMs)
        assertSame(screen, active.createdOnSurface)
        assertTrue(active.playing)
        assertFalse(nextStandby.playing)
    }

    @Test
    fun `a short pause only pauses`() = runTest {
        val factory = FakeEngineFactory()
        start(factory)

        playWhenReady.value = false
        advanceTimeBy(5_000)
        playWhenReady.value = true
        runCurrent()

        assertEquals(2, factory.created.size)
        assertEquals(listOf("play", "pause", "play"), factory.created[0].actions)
    }

    @Test
    fun `a new surface goes to the active engine and to the standby it switches to`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)
        val (active, standby) = factory.created
        val recreated = newSurface()

        supervisor.setSurface(recreated)
        assertSame(recreated, active.surface)

        active.error.value = PlaybackException.ERROR_CODE_DECODING_FAILED
        runCurrent()
        assertSame(recreated, standby.surface)
    }

    @Test
    fun `a clip that doesn't loop stops on its end without a switch`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)
        supervisor.setLooping(false)

        advanceTimeBy(12_000)

        val (active, standby) = factory.created
        assertEquals(WallperPlayerState.Ended, supervisor.state.value)
        assertEquals(10_000, active.positionMs)
        assertFalse(active.released)
        assertFalse(standby.loops)
        assertEquals(2, factory.created.size)
    }

    @Test
    fun `a play after the end starts the clip over`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)
        supervisor.setLooping(false)
        advanceTimeBy(12_000)

        playWhenReady.value = false
        runCurrent()
        playWhenReady.value = true
        runCurrent()

        val active = factory.created.first()
        assertTrue("#1 seekTo 0" in factory.journal)
        assertTrue(active.playing)
        assertEquals(WallperPlayerState.Playing, supervisor.state.value)
    }

    @Test
    fun `seek to start moves the playing engine and where new engines start`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)
        advanceTimeBy(3_000)

        supervisor.seekToStart()
        assertTrue("#1 seekTo 0" in factory.journal)

        // Paused for long, the engines are released: the next ones start at the clip start.
        advanceTimeBy(3_000)
        playWhenReady.value = false
        advanceTimeBy(20_000)
        supervisor.seekToStart()
        playWhenReady.value = true
        runCurrent()
        assertEquals(0, factory.created[2].startPositionMs)
    }

    @Test
    fun `looping reaches every engine, the ones made later too`() = runTest {
        val factory = FakeEngineFactory()
        val supervisor = start(factory)

        supervisor.setLooping(false)
        val (stalled, standby) = factory.created
        advanceTimeBy(2_000)
        stalled.healthy = false
        advanceTimeBy(1_000)

        assertFalse(stalled.loops)
        assertFalse(standby.loops)
        assertFalse(factory.created[2].loops)
    }

    private fun newSurface() = Surface(SurfaceTexture(0))
}
