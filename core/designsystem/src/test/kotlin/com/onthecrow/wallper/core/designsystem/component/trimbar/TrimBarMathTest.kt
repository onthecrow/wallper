package com.onthecrow.wallper.core.designsystem.component.trimbar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrimBarMathTest {

    // 64 px per second, as at the default 64 dp per second on a 1x screen.
    private val pxPerMs = 0.064f

    @Test
    fun `the scale puts a moment by its distance from the playhead`() {
        assertEquals(64f, offsetOf(timeMs = 3_000f, positionMs = 2_000f, pxPerMs = pxPerMs), DELTA)
        assertEquals(-128f, offsetOf(timeMs = 0f, positionMs = 2_000f, pxPerMs = pxPerMs), DELTA)
    }

    @Test
    fun `with the playhead inside, the handles sit at the ends of the kept frames`() {
        val layout = layout(positionMs = 5_000f)

        // The kept part starts 3 s (192 px) before the playhead: its frames, then the gap.
        assertEquals(-200f, layout.startHandleRight, DELTA)
        assertEquals(-216f, layout.startHandleLeft, DELTA)
        assertEquals(200f, layout.endHandleLeft, DELTA)
        assertEquals(216f, layout.endHandleRight, DELTA)
        assertEquals(-GAP, layout.frameShift(handlesBefore = 1, rightOfPlayhead = false), DELTA)
        assertEquals(GAP, layout.frameShift(handlesBefore = 1, rightOfPlayhead = true), DELTA)
        // Frames beyond a handle go on from its outer edge.
        assertEquals(-216f, (2_000f - 5_000f) * pxPerMs + layout.frameShift(handlesBefore = 0, rightOfPlayhead = false), DELTA)
        assertEquals(216f, (8_000f - 5_000f) * pxPerMs + layout.frameShift(handlesBefore = 2, rightOfPlayhead = true), DELTA)
    }

    @Test
    fun `at the start of the kept part the playhead sits between the start handle and the frames`() {
        val layout = layout(positionMs = 2_000f)

        assertEquals(-HANDLE - GAP, layout.startHandleLeft, DELTA)
        assertEquals(-GAP, layout.startHandleRight, DELTA)
        assertEquals(GAP, layout.frameShift(handlesBefore = 1, rightOfPlayhead = true), DELTA)
    }

    @Test
    fun `at the end of the kept part the playhead sits between the frames and the end handle`() {
        val layout = layout(positionMs = 8_000f)

        assertEquals(GAP, layout.endHandleLeft, DELTA)
        assertEquals(HANDLE + GAP, layout.endHandleRight, DELTA)
        assertEquals(-GAP, layout.frameShift(handlesBefore = 1, rightOfPlayhead = false), DELTA)
    }

    @Test
    fun `outside the kept part the whole track is on one side of the playhead`() {
        val before = layout(positionMs = 1_000f)
        val after = layout(positionMs = 9_000f)

        assertEquals(64f + GAP, before.startHandleLeft, DELTA)
        assertEquals(448f + HANDLE + GAP, before.endHandleLeft, DELTA)
        assertEquals(-448f - HANDLE - GAP, after.startHandleRight, DELTA)
        assertEquals(-64f - GAP, after.endHandleRight, DELTA)
    }

    @Test
    fun `a handle's edge gives back its moment`() {
        for (positionMs in listOf(1_000f, 2_000f, 5_000f, 8_000f, 9_000f)) {
            val layout = layout(positionMs)
            assertEquals(2_000f, layout.handleTimeAt(TrimHandle.Start, layout.startHandleRight), DELTA, "start at $positionMs")
            assertEquals(8_000f, layout.handleTimeAt(TrimHandle.End, layout.endHandleLeft), DELTA, "end at $positionMs")
        }
    }

    @Test
    fun `a handle dragged across the playhead holds on to it, then jumps over`() {
        val layout = layout(positionMs = 5_000f)

        // Left of the playhead the start handle's edge ends at -GAP; right of it, a handle and the gap later.
        assertEquals(4_000f, layout.handleTimeAt(TrimHandle.Start, -GAP - 64f), DELTA)
        assertEquals(5_000f, layout.handleTimeAt(TrimHandle.Start, 0f), DELTA)
        assertEquals(5_000f, layout.handleTimeAt(TrimHandle.Start, HANDLE + GAP), DELTA)
        assertEquals(6_000f, layout.handleTimeAt(TrimHandle.Start, HANDLE + GAP + 64f), DELTA)
        // The end handle the other way round.
        assertEquals(6_000f, layout.handleTimeAt(TrimHandle.End, GAP + 64f), DELTA)
        assertEquals(5_000f, layout.handleTimeAt(TrimHandle.End, -10f), DELTA)
        assertEquals(4_000f, layout.handleTimeAt(TrimHandle.End, -HANDLE - GAP - 64f), DELTA)
    }

    @Test
    fun `a handle that can't reach the playhead never holds on to it`() {
        // The playhead is past the end: the start handle is always left of it.
        assertEquals(8_000f, layout(positionMs = 9_000f).handleTimeAt(TrimHandle.Start, -HANDLE - GAP - 64f), DELTA)
        // The playhead is before the start: the end handle is always right of it.
        assertEquals(2_000f, layout(positionMs = 1_000f).handleTimeAt(TrimHandle.End, HANDLE + GAP + 64f), DELTA)
    }

    @Test
    fun `dragging the strip right goes back in time`() {
        assertEquals(4_000f, scrub(positionMs = 5_000f, dragPx = 64f, pxPerMs = pxPerMs, startMs = 0f, endMs = 10_000f), DELTA)
        assertEquals(6_000f, scrub(positionMs = 5_000f, dragPx = -64f, pxPerMs = pxPerMs, startMs = 0f, endMs = 10_000f), DELTA)
    }

    @Test
    fun `the strip stops at the handles`() {
        assertEquals(2_000f, scrub(positionMs = 3_000f, dragPx = 640f, pxPerMs = pxPerMs, startMs = 2_000f, endMs = 8_000f), DELTA)
        assertEquals(8_000f, scrub(positionMs = 7_000f, dragPx = -640f, pxPerMs = pxPerMs, startMs = 2_000f, endMs = 8_000f), DELTA)
    }

    @Test
    fun `a handle far from the playhead follows the finger`() {
        val move = move(TrimHandle.Start, rawMs = 1_500f, positionMs = 5_000f)

        assertEquals(HandleMove(1_500f, snapped = false), move)
    }

    @Test
    fun `a handle within the snap distance sticks to the playhead`() {
        // 50 ms is 3.2 px at 64 px per second: inside 4 px.
        assertEquals(HandleMove(5_000f, snapped = true), move(TrimHandle.Start, rawMs = 4_950f, positionMs = 5_000f))
        assertEquals(HandleMove(5_000f, snapped = true), move(TrimHandle.End, rawMs = 5_050f, positionMs = 5_000f))
    }

    @Test
    fun `a handle just beyond the snap distance does not stick`() {
        // 100 ms is 6.4 px.
        assertEquals(HandleMove(4_900f, snapped = false), move(TrimHandle.Start, rawMs = 4_900f, positionMs = 5_000f))
    }

    @Test
    fun `handles stay inside the video`() {
        assertEquals(0f, move(TrimHandle.Start, rawMs = -700f, positionMs = 5_000f).timeMs, DELTA)
        assertEquals(10_000f, move(TrimHandle.End, rawMs = 12_000f, positionMs = 5_000f).timeMs, DELTA)
    }

    @Test
    fun `handles keep the minimum length between them`() {
        val start = move(TrimHandle.Start, rawMs = 7_900f, startMs = 0f, endMs = 8_000f, positionMs = 2_000f)
        val end = move(TrimHandle.End, rawMs = 1_100f, startMs = 1_000f, endMs = 10_000f, positionMs = 5_000f)

        assertEquals(7_000f, start.timeMs, DELTA)
        assertEquals(2_000f, end.timeMs, DELTA)
    }

    @Test
    fun `a handle does not stick to a playhead it can't reach`() {
        // The playhead is 500 ms before the end handle: closer than the minimum length.
        val move = move(TrimHandle.Start, rawMs = 7_480f, startMs = 0f, endMs = 8_000f, positionMs = 7_500f)

        assertFalse(move.snapped)
        assertEquals(7_000f, move.timeMs, DELTA)
    }

    @Test
    fun `a video shorter than the minimum keeps both handles at its ends`() {
        val start = move(TrimHandle.Start, rawMs = 300f, startMs = 0f, endMs = 600f, durationMs = 600f, positionMs = 0f)
        val end = move(TrimHandle.End, rawMs = 300f, startMs = 0f, endMs = 600f, durationMs = 600f, positionMs = 0f)

        assertEquals(0f, start.timeMs, DELTA)
        assertEquals(600f, end.timeMs, DELTA)
    }

    @Test
    fun `a touch near a handle grabs it, elsewhere it drags the strip`() {
        assertEquals(TrimHandle.Start, handleAt(offsetPx = -110f, startOffsetPx = -100f, endOffsetPx = 300f, touchRadiusPx = 24f))
        assertEquals(TrimHandle.End, handleAt(offsetPx = 320f, startOffsetPx = -100f, endOffsetPx = 300f, touchRadiusPx = 24f))
        assertNull(handleAt(offsetPx = 0f, startOffsetPx = -100f, endOffsetPx = 300f, touchRadiusPx = 24f))
    }

    @Test
    fun `between close handles the nearer one wins, on a tie the side of the touch`() {
        assertEquals(TrimHandle.End, handleAt(offsetPx = 32f, startOffsetPx = 20f, endOffsetPx = 40f, touchRadiusPx = 24f))
        assertEquals(TrimHandle.Start, handleAt(offsetPx = 25f, startOffsetPx = 30f, endOffsetPx = 30f, touchRadiusPx = 24f))
        assertEquals(TrimHandle.End, handleAt(offsetPx = 35f, startOffsetPx = 30f, endOffsetPx = 30f, touchRadiusPx = 24f))
    }

    @Test
    fun `a handle next to the playhead can't be taken from across it`() {
        // The playhead sits at the start: the start handle's middle is 16 px left of it.
        assertNull(handleAt(offsetPx = 0f, startOffsetPx = -16f, endOffsetPx = 300f, touchRadiusPx = 24f))
        assertNull(handleAt(offsetPx = 6f, startOffsetPx = -16f, endOffsetPx = 300f, touchRadiusPx = 24f))
        assertEquals(TrimHandle.Start, handleAt(offsetPx = -4f, startOffsetPx = -16f, endOffsetPx = 300f, touchRadiusPx = 24f))
        // And at the end, the end handle 16 px right of it.
        assertNull(handleAt(offsetPx = -6f, startOffsetPx = -300f, endOffsetPx = 16f, touchRadiusPx = 24f))
        assertEquals(TrimHandle.End, handleAt(offsetPx = 4f, startOffsetPx = -300f, endOffsetPx = 16f, touchRadiusPx = 24f))
    }

    @Test
    fun `the strip scrolls only near the edges, faster closer to them`() {
        fun speed(fingerX: Float) = autoScrollSpeed(fingerX, startX = 200f, widthPx = 400f, edgePx = 48f, maxSpeedPx = 500f)

        assertEquals(0f, speed(200f), DELTA)
        assertEquals(-250f, speed(24f), DELTA)
        assertEquals(250f, speed(376f), DELTA)
        assertEquals(500f, speed(420f), DELTA)
        assertEquals(-500f, speed(-10f), DELTA)
    }

    @Test
    fun `a drag that starts near an edge scrolls only once it goes on towards it`() {
        fun speed(fingerX: Float) = autoScrollSpeed(fingerX, startX = 20f, widthPx = 400f, edgePx = 48f, maxSpeedPx = 500f)

        assertEquals(0f, speed(20f), DELTA)
        assertEquals(0f, speed(30f), DELTA)
        assertEquals(-250f, speed(10f), DELTA)
        assertEquals(-500f, speed(0f), DELTA)
        // The other edge is as far as ever.
        assertEquals(250f, speed(376f), DELTA)
    }

    @Test
    fun `the longer a handle is held at an edge the faster the strip goes`() {
        assertEquals(1f, autoScrollBoost(heldSeconds = 0f, doublingSeconds = 1f, maxBoost = 32f), DELTA)
        assertEquals(2f, autoScrollBoost(heldSeconds = 1f, doublingSeconds = 1f, maxBoost = 32f), DELTA)
        assertEquals(8f, autoScrollBoost(heldSeconds = 3f, doublingSeconds = 1f, maxBoost = 32f), DELTA)
        assertEquals(32f, autoScrollBoost(heldSeconds = 20f, doublingSeconds = 1f, maxBoost = 32f), DELTA)
    }

    @Test
    fun `beyond the handles things fade out over the fade width`() {
        // Opaque between the outer edges of the handles, at -216 and 216 px.
        assertEquals(1f, fadeAlpha(x = -216f, opaqueFromX = -216f, opaqueToX = 216f, fadePx = 32f), DELTA)
        assertEquals(1f, fadeAlpha(x = 100f, opaqueFromX = -216f, opaqueToX = 216f, fadePx = 32f), DELTA)
        assertEquals(0.75f, fadeAlpha(x = -224f, opaqueFromX = -216f, opaqueToX = 216f, fadePx = 32f), DELTA)
        assertEquals(0.5f, fadeAlpha(x = 232f, opaqueFromX = -216f, opaqueToX = 216f, fadePx = 32f), DELTA)
        assertEquals(0f, fadeAlpha(x = -300f, opaqueFromX = -216f, opaqueToX = 216f, fadePx = 32f), DELTA)
    }

    @Test
    fun `the scale shows the whole seconds in view and inside the video`() {
        assertEquals(2..5, secondsBetween(fromMs = 1_200f, toMs = 5_800f, durationMs = 16_000f))
        assertEquals(0..3, secondsBetween(fromMs = -3_000f, toMs = 3_000f, durationMs = 16_000f))
        assertEquals(14..15, secondsBetween(fromMs = 13_500f, toMs = 20_000f, durationMs = 15_600f))
        assertTrue(secondsBetween(fromMs = 1_100f, toMs = 1_900f, durationMs = 16_000f).isEmpty())
    }

    @Test
    fun `even seconds are named, odd ones are dots`() {
        assertEquals(listOf(true, false, true, false), (0..3).map(::isLabelled))
    }

    @Test
    fun `moments read as minutes and seconds`() {
        assertEquals("00:00", timeLabel(0))
        assertEquals("00:02", timeLabel(2_999))
        assertEquals("01:04", timeLabel(64_000))
        assertEquals("75:00", timeLabel(4_500_000))
        assertEquals("00:00", timeLabel(-20))
    }

    @Test
    fun `a tile shows the frame covering its middle`() {
        assertEquals(0, frameIndex(tileMiddleMs = 200f, intervalMs = 500, count = 10))
        assertEquals(3, frameIndex(tileMiddleMs = 1_750f, intervalMs = 500, count = 10))
        assertEquals(9, frameIndex(tileMiddleMs = 9_000f, intervalMs = 500, count = 10))
    }

    private fun layout(positionMs: Float) = TrimLayout(
        positionMs = positionMs,
        startMs = 2_000f,
        endMs = 8_000f,
        pxPerMs = pxPerMs,
        handlePx = HANDLE,
        gapPx = GAP,
    )

    private fun move(
        handle: TrimHandle,
        rawMs: Float,
        positionMs: Float,
        startMs: Float = 0f,
        endMs: Float = 10_000f,
        durationMs: Float = 10_000f,
    ) = moveHandle(
        handle = handle,
        rawMs = rawMs,
        startMs = startMs,
        endMs = endMs,
        durationMs = durationMs,
        positionMs = positionMs,
        minDurationMs = 1_000f,
        snapPx = 4f,
        pxPerMs = pxPerMs,
    )

    private companion object {
        const val DELTA = 0.01f
        const val HANDLE = 16f
        const val GAP = 8f
    }
}
