package com.onthecrow.wallper.core.engine.impl.engine

import android.view.Surface
import com.onthecrow.wallper.core.engine.WallpaperSettings
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.core.player.WallperPlayer
import com.onthecrow.wallper.core.player.WallperPlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlaybackControlTest {

    private val player = FakePlayer()
    private val settings = MutableStateFlow(WallpaperSettings())
    private val savingPower = MutableStateFlow(false)
    private val screenOffs = MutableSharedFlow<Unit>()

    private fun TestScope.control() =
        VideoPlaybackControl(player, settings, savingPower, screenOffs, backgroundScope + UnconfinedTestDispatcher(testScheduler))

    @Test
    fun `plays while visible and pauses while hidden`() = runTest {
        val control = control()
        assertTrue(player.playing)

        control.setVisible(false)
        assertFalse(player.playing)

        control.setVisible(true)
        assertTrue(player.playing)
    }

    @Test
    fun `battery saver pauses the video when the user wants it to`() = runTest {
        control()

        savingPower.value = true
        assertFalse(player.playing)

        settings.value = WallpaperSettings(pausesToSaveBattery = false)
        assertTrue(player.playing)
    }

    @Test
    fun `loop goes on from where it stopped, screen off or not`() = runTest {
        val control = control()

        turnScreenOffAndOn(control)

        assertEquals(0, player.seeksToStart)
        assertTrue(player.loops)
    }

    @Test
    fun `restart goes on after an app, and starts over after the screen was off`() = runTest {
        settings.value = WallpaperSettings(playback = VideoPlayback.RESTART)
        val control = control()

        // Out to an app and back to the home screen.
        control.setVisible(false)
        control.setVisible(true)
        assertEquals(0, player.seeksToStart)

        turnScreenOffAndOn(control)
        assertEquals(1, player.seeksToStart)
        assertTrue(player.loops)
    }

    @Test
    fun `once doesn't loop and starts over only after the screen was off`() = runTest {
        settings.value = WallpaperSettings(playback = VideoPlayback.ONCE)
        val control = control()
        assertFalse(player.loops)

        control.setVisible(false)
        control.setVisible(true)
        assertEquals(0, player.seeksToStart)

        turnScreenOffAndOn(control)
        assertEquals(1, player.seeksToStart)
        assertTrue(player.playing)
    }

    @Test
    fun `coming back from Battery Saver isn't a new wake`() = runTest {
        settings.value = WallpaperSettings(playback = VideoPlayback.RESTART)
        control()

        savingPower.value = true
        savingPower.value = false

        assertEquals(0, player.seeksToStart)
        assertTrue(player.playing)
    }

    @Test
    fun `a wake the battery saver held starts over once it plays`() = runTest {
        settings.value = WallpaperSettings(playback = VideoPlayback.RESTART)
        val control = control()
        savingPower.value = true

        turnScreenOffAndOn(control)
        assertEquals(0, player.seeksToStart)

        savingPower.value = false
        assertEquals(1, player.seeksToStart)
    }

    private suspend fun turnScreenOffAndOn(control: VideoPlaybackControl) {
        control.setVisible(false)
        screenOffs.emit(Unit)
        control.setVisible(true)
    }

    private class FakePlayer : WallperPlayer {
        var playing = false
        var loops = true
            private set
        var seeksToStart = 0

        override val state: StateFlow<WallperPlayerState> = MutableStateFlow(WallperPlayerState.Idle)
        override fun setVideoSurface(surface: Surface?) = Unit
        override fun play() {
            playing = true
        }
        override fun pause() {
            playing = false
        }
        override fun setLooping(looping: Boolean) {
            loops = looping
        }
        override fun seekToStart() {
            seeksToStart++
        }
        override fun release() = Unit
    }
}
