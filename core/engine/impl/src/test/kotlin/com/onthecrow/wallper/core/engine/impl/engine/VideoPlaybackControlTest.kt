package com.onthecrow.wallper.core.engine.impl.engine

import android.view.Surface
import com.onthecrow.wallper.core.engine.VideoWallpaperSettings
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.core.player.WallperPlayer
import com.onthecrow.wallper.core.player.WallperPlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val settings = MutableStateFlow(VideoWallpaperSettings())
    private val savingPower = MutableStateFlow(false)

    private fun TestScope.control() =
        VideoPlaybackControl(player, settings, savingPower, backgroundScope + UnconfinedTestDispatcher(testScheduler))

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

        settings.value = VideoWallpaperSettings(pausesToSaveBattery = false)
        assertTrue(player.playing)
    }

    @Test
    fun `loop goes on from where it stopped`() = runTest {
        val control = control()

        control.setVisible(false)
        control.setVisible(true)

        assertEquals(0, player.seeksToStart)
        assertTrue(player.loops)
    }

    @Test
    fun `restart starts over at each show, and only then`() = runTest {
        settings.value = VideoWallpaperSettings(playback = VideoPlayback.RESTART)
        val control = control()
        assertEquals(0, player.seeksToStart)

        control.setVisible(false)
        control.setVisible(true)
        assertEquals(1, player.seeksToStart)

        // Back from Battery Saver: the same show goes on.
        savingPower.value = true
        savingPower.value = false
        assertEquals(1, player.seeksToStart)
        assertTrue(player.loops)
    }

    @Test
    fun `once plays without looping from the start of each show`() = runTest {
        settings.value = VideoWallpaperSettings(playback = VideoPlayback.ONCE)
        val control = control()
        assertFalse(player.loops)

        control.setVisible(false)
        control.setVisible(true)

        assertEquals(1, player.seeksToStart)
        assertTrue(player.playing)
    }

    @Test
    fun `a show the battery saver held starts over once it plays`() = runTest {
        settings.value = VideoWallpaperSettings(playback = VideoPlayback.RESTART)
        val control = control()
        savingPower.value = true

        control.setVisible(false)
        control.setVisible(true)
        assertEquals(0, player.seeksToStart)

        savingPower.value = false
        assertEquals(1, player.seeksToStart)
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
