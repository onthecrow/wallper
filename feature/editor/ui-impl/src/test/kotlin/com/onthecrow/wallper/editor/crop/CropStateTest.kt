package com.onthecrow.wallper.editor.crop

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.IntSize
import com.onthecrow.wallper.core.model.CropRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CropStateTest {

    private val media = IntSize(1920, 1080)

    @Test
    fun `before layout a new crop is the middle of the media at full height`() {
        val state = CropState(media, screenAspectRatio = 0.45f, startCrop = null)

        assertNull(state.placement)
        assertEquals(CropRect(left = 717, top = 0, right = 1203, bottom = 1080), state.crop())
    }

    @Test
    fun `an untouched crop comes back exactly, whatever the frame's size`() {
        val saved = CropRect(left = 101, top = 3, right = 587, bottom = 1079, rotation = 2.5f)
        val state = CropState(media, screenAspectRatio = 0.45f, startCrop = saved)

        state.onFrameSize(450f, 1000f)
        state.onFrameSize(360f, 800f)

        assertEquals(saved, state.crop())
    }

    @Test
    fun `a gesture changes the crop and a new frame size keeps it`() {
        val state = CropState(media, screenAspectRatio = 0.45f, startCrop = null)
        state.onFrameSize(450f, 1000f)

        state.onGestureStart()
        assertTrue(state.isInteracting)
        state.onGesture(0f, 0f, panX = 0f, panY = 0f, zoom = 2f, rotation = 0f)
        val zoomed = state.crop()
        state.onFrameSize(360f, 800f)

        assertNotEquals(CropRect(717, 0, 1203, 1080), zoomed)
        assertEquals(zoomed.width.toFloat(), state.crop().width.toFloat(), 2f)
        assertEquals(zoomed.centerX, state.crop().centerX, 2f)
    }

    @Test
    fun `the crop to save always covers the frame, even before the picture springs back`() {
        val state = CropState(media, screenAspectRatio = 0.45f, startCrop = null)
        state.onFrameSize(450f, 1000f)

        state.onGestureStart()
        state.onGesture(0f, 0f, panX = 2_000f, panY = 0f, zoom = 1f, rotation = 0f)

        val crop = state.crop()
        assertTrue(crop.left >= 0 && crop.right <= 1920, "$crop")
    }

    @Test
    fun `media without a size waits for one`() {
        val state = CropState(IntSize(0, 0), screenAspectRatio = 0.45f, startCrop = null)

        state.onFrameSize(450f, 1000f)

        assertNull(state.placement)
        assertFalse(state.isInteracting)
    }

    @Test
    fun `turning near level snaps there once, shows the line and lets go past it`() {
        val state = laidOut()

        state.onGestureStart()
        // Starting level: holding it there is no snap, but the line shows once the fingers turn.
        assertFalse(state.turn(2f))
        assertEquals(0f, state.placement?.rotation)
        assertTrue(state.guides.level)
        // Past the snap the picture follows the fingers.
        assertFalse(state.turn(4f))
        assertEquals(6f, state.placement!!.rotation, 1e-4f)
        assertFalse(state.guides.level)
        // Back near level: it snaps, once.
        assertTrue(state.turn(-3f))
        assertFalse(state.turn(-0.5f))
        assertEquals(0f, state.placement!!.rotation, 1e-4f)
        assertTrue(state.guides.level)
    }

    @Test
    fun `a drag of a level picture neither snaps level nor shows the level line`() {
        val state = laidOut()

        state.onGestureStart()

        assertFalse(state.onGesture(0f, 0f, panX = 30f, panY = 0f, zoom = 1.2f, rotation = 0f))
        assertFalse(state.guides.level)
    }

    @Test
    fun `a drag near the middle snaps the picture's centre there, once, and lets go past it`() {
        // 3% of the frame's shorter side: 13.5 pixels.
        val state = laidOut()
        state.onGestureStart()

        // Away from the middle across, still centred up and down.
        assertFalse(state.drag(40f, 5f))
        assertEquals(40f, state.placement!!.offsetX, 1e-3f)
        assertEquals(0f, state.placement!!.offsetY)
        assertTrue(state.guides.centerY)
        assertFalse(state.guides.centerX)
        // Back within reach of the middle: it snaps, once, while the fingers stay off it.
        assertTrue(state.drag(-30f, 0f))
        assertEquals(0f, state.placement!!.offsetX)
        assertTrue(state.guides.centerX)
        assertFalse(state.drag(-5f, 0f))
        assertEquals(0f, state.placement!!.offsetX)
        // Past it the picture follows the fingers again, from where they are.
        assertFalse(state.drag(-20f, 0f))
        assertEquals(-15f, state.placement!!.offsetX, 1e-3f)
        assertFalse(state.guides.centerX)
    }

    @Test
    fun `the guides go when the fingers let go`() = runTest {
        val state = laidOut()
        state.onGestureStart()
        state.drag(5f, 5f)
        assertTrue(state.guides.centerX)

        state.onGestureEnd(animationScope())

        assertEquals(Snaps.NONE, state.guides)
    }

    @Test
    fun `the line goes when the fingers let go`() = runTest {
        val state = laidOut()
        state.onGestureStart()
        state.turn(1f)

        state.onGestureEnd(animationScope())

        assertEquals(Snaps.NONE, state.guides)
        assertFalse(state.isInteracting)
    }

    @Test
    fun `a new crop reset goes back to the middle and is no change any more`() = runTest {
        val state = laidOut()
        val start = state.crop()
        state.onGestureStart()
        state.onGesture(0f, 0f, panX = 40f, panY = 0f, zoom = 2f, rotation = 30f)
        state.onGestureEnd(animationScope())
        assertTrue(state.isChanged)

        state.reset(animationScope())

        assertFalse(state.isChanged)
        advanceUntilIdle()
        assertEquals(start, state.crop())
    }

    @Test
    fun `an edited crop reset gives back the saved crop exactly`() = runTest {
        val saved = CropRect(left = 101, top = 3, right = 587, bottom = 1079, rotation = 2.5f)
        val state = CropState(media, screenAspectRatio = 0.45f, startCrop = saved)
        state.onFrameSize(450f, 1000f)
        assertFalse(state.isChanged)
        state.onGestureStart()
        state.onGesture(0f, 0f, panX = 0f, panY = 0f, zoom = 1.5f, rotation = 0f)
        assertTrue(state.isChanged)

        state.reset(animationScope())
        advanceUntilIdle()

        assertEquals(saved, state.crop())
        assertFalse(state.isChanged)
    }

    private fun laidOut() = CropState(media, screenAspectRatio = 0.45f, startCrop = null).apply { onFrameSize(450f, 1000f) }

    private fun CropState.turn(degrees: Float) = onGesture(0f, 0f, 0f, 0f, zoom = 1f, rotation = degrees)

    private fun CropState.drag(x: Float, y: Float) = onGesture(0f, 0f, panX = x, panY = y, zoom = 1f, rotation = 0f)

    /** Animations get a frame every 16 ms, as fast as the test runs them. */
    private fun TestScope.animationScope() = CoroutineScope(coroutineContext + object : MonotonicFrameClock {
        private var time = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 16_000_000L
            return onFrame(time)
        }
    })
}
