package com.onthecrow.wallper.editor.crop

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.IntSize
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.reshaped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FramingStateTest {

    private val media = IntSize(3840, 2160)
    private val portrait = CropRect(left = 1400, top = 0, right = 2372, bottom = 2160)

    @Test
    fun `a new wallpaper's turned crop is derived from the first until the user moves it`() {
        val state = laidOut(start = null)

        state.turn()

        assertTrue(state.isTurned)
        assertFalse(state.hasOwnTurned)
        assertNull(state.framing().turned)
        assertEquals(state.base.crop().reshaped(1f / ASPECT, media.width, media.height), state.turned.crop())
    }

    @Test
    fun `a moved turned crop is the user's own, saved, and kept across turns`() {
        val state = laidOut(start = null)
        state.turn()

        state.onGestureStart()
        state.turned.onGesture(0f, 0f, panX = 30f, panY = 0f, zoom = 1.5f, rotation = 0f)
        val own = state.turned.crop()
        state.turn()
        state.turn()

        assertTrue(state.hasOwnTurned)
        assertTrue(state.isChanged)
        assertEquals(own, state.turned.crop())
        assertEquals(Framing(state.base.crop(), own), state.framing())
    }

    @Test
    fun `a derived turned crop follows the first one`() {
        val state = laidOut(start = Framing(portrait))
        state.onGestureStart()
        state.base.onGesture(0f, 0f, panX = 100f, panY = 0f, zoom = 1f, rotation = 0f)

        state.turn()

        assertEquals(state.base.crop().reshaped(1f / ASPECT, media.width, media.height), state.turned.crop())
        assertNull(state.framing().turned)
    }

    @Test
    fun `an edit opened with the screen turned keeps its first crop first`() {
        // Saved on a phone held upright, opened on a screen held across.
        val state = laidOut(start = Framing(portrait), aspect = 1f / ASPECT)

        assertTrue(state.hasOwnTurned.not())
        assertEquals(portrait, state.turned.crop())
        assertEquals(Framing(portrait), state.framing())

        state.onGestureStart()
        state.base.onGesture(0f, 0f, panX = 50f, panY = 0f, zoom = 1f, rotation = 0f)

        assertTrue(state.hasOwnTurned)
        assertEquals(Framing(portrait, state.base.crop()), state.framing())
    }

    @Test
    fun `a reset brings both crops back, own or derived as they were`() = runTest {
        val landscape = CropRect(0, 500, 3000, 1350)
        val state = laidOut(start = Framing(portrait, landscape))
        state.onGestureStart()
        state.base.onGesture(0f, 0f, panX = 80f, panY = 0f, zoom = 1f, rotation = 0f)
        state.base.onGestureEnd(animationScope())
        advanceUntilIdle()
        assertTrue(state.isChanged)

        state.reset(animationScope())
        advanceUntilIdle()

        assertFalse(state.isChanged)
        assertTrue(state.hasOwnTurned)
        assertEquals(Framing(portrait, landscape), state.framing())
    }

    @Test
    fun `restored on a screen turned since, the crops swap places`() {
        val restored = FramingState.Restored(
            base = portrait,
            turned = CropRect(0, 500, 3000, 1350),
            ownBase = true,
            ownTurned = true,
            isTurned = false,
        ).swapped()

        val state = FramingState(media, 1f / ASPECT, start = Framing(portrait), restored = restored)

        assertEquals(portrait, state.turned.crop())
        assertEquals(CropRect(0, 500, 3000, 1350), state.base.crop())
        assertTrue(state.isTurned)
    }

    private fun laidOut(start: Framing?, aspect: Float = ASPECT) = FramingState(media, aspect, start).apply {
        base.onFrameSize(450f, 450f / aspect)
        turned.onFrameSize(300f, 300f * aspect)
    }

    /** Animations get a frame every 16 ms, as fast as the test runs them. */
    private fun TestScope.animationScope() = CoroutineScope(coroutineContext + object : MonotonicFrameClock {
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 16_000_000L
            return onFrame(time)
        }
    })

    private companion object {
        const val ASPECT = 0.45f
    }
}
