package com.onthecrow.wallper.core.designsystem.component.trimbar

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The trim bar's geometry, free of Compose so it runs on the JVM. Times are milliseconds of the
// video, distances are pixels. The playhead stays at the centre of the bar and the strip moves
// under it: an offset is measured from the playhead, positive to the right.

/** A trim handle: the start or the end of the kept part. */
internal enum class TrimHandle { Start, End }

/** How far right of the playhead the moment [timeMs] is on the scale while [positionMs] is under the playhead. */
internal fun offsetOf(timeMs: Float, positionMs: Float, pxPerMs: Float): Float = (timeMs - positionMs) * pxPerMs

/**
 * The moment under the playhead after the strip is dragged [dragPx] to the right: the strip moves with
 * the finger, so the playhead goes back in time. It never leaves the kept part.
 */
internal fun scrub(positionMs: Float, dragPx: Float, pxPerMs: Float, startMs: Float, endMs: Float): Float =
    (positionMs - dragPx / pxPerMs).coerceIn(startMs, endMs)

/** Where a dragged handle lands; [snapped] when it was pulled onto the playhead. */
internal data class HandleMove(val timeMs: Float, val snapped: Boolean)

/**
 * Where [handle] lands when the finger puts it at [rawMs]. The kept part stays inside the video and at
 * least [minDurationMs] long (shorter only when the whole video is). Within [snapPx] of the playhead at
 * [positionMs] the handle sticks to it, unless the playhead is out of the handle's reach.
 *
 * @param startMs the start of the kept part, the other handle when [handle] is the end.
 * @param endMs the end of the kept part, the other handle when [handle] is the start.
 */
internal fun moveHandle(
    handle: TrimHandle,
    rawMs: Float,
    startMs: Float,
    endMs: Float,
    durationMs: Float,
    positionMs: Float,
    minDurationMs: Float,
    snapPx: Float,
    pxPerMs: Float,
): HandleMove {
    val reach = when (handle) {
        TrimHandle.Start -> 0f..max(0f, endMs - minDurationMs)
        TrimHandle.End -> min(durationMs, startMs + minDurationMs)..durationMs
    }
    val snaps = abs(rawMs - positionMs) * pxPerMs <= snapPx && positionMs in reach
    return HandleMove(timeMs = if (snaps) positionMs else rawMs.coerceIn(reach), snapped = snaps)
}

/**
 * The handle a touch [offsetPx] from the playhead grabs: the nearer one within [touchRadiusPx], or null
 * when the touch drags the strip. A handle can't be taken from across the playhead, so a drag that starts
 * on the playhead moves the strip even with a handle next to it. When both are equally near, the side of
 * the touch decides.
 */
internal fun handleAt(offsetPx: Float, startOffsetPx: Float, endOffsetPx: Float, touchRadiusPx: Float): TrimHandle? {
    fun distanceTo(handleOffsetPx: Float) =
        if (handleOffsetPx * offsetPx <= 0f) Float.POSITIVE_INFINITY else abs(offsetPx - handleOffsetPx)
    val toStart = distanceTo(startOffsetPx)
    val toEnd = distanceTo(endOffsetPx)
    return when {
        min(toStart, toEnd) > touchRadiusPx -> null
        toStart < toEnd -> TrimHandle.Start
        toEnd < toStart -> TrimHandle.End
        offsetPx < startOffsetPx -> TrimHandle.Start
        else -> TrimHandle.End
    }
}

/**
 * Where the parts of the strip go, in px right of the playhead. The strip is the video at [pxPerMs] with a
 * handle [handlePx] wide put in at each end of the kept part, parted by [gapPx] on either side of the
 * playhead. The playhead and the handles push the frames aside rather than cover them, so a handle never
 * runs under the playhead: with the playhead at an end of the kept part, it sits between that end's handle
 * and the frames.
 *
 * A handle's inner edge is its moment: the kept frames start right after the start handle and end right
 * before the end one.
 */
internal class TrimLayout(
    private val positionMs: Float,
    private val startMs: Float,
    private val endMs: Float,
    private val pxPerMs: Float,
    val handlePx: Float,
    val gapPx: Float,
) {
    /** Handles left of the playhead: the start one from the start of the kept part on, the end one past its end. */
    private val handlesBeforePlayhead = (if (positionMs >= startMs) 1 else 0) + (if (positionMs > endMs) 1 else 0)

    val startHandleLeft: Float =
        (startMs - positionMs) * pxPerMs - handlesBeforePlayhead * handlePx + side(rightOfPlayhead = positionMs < startMs)
    val startHandleRight: Float get() = startHandleLeft + handlePx

    val endHandleLeft: Float =
        (endMs - positionMs) * pxPerMs + (1 - handlesBeforePlayhead) * handlePx + side(rightOfPlayhead = positionMs <= endMs)
    val endHandleRight: Float get() = endHandleLeft + handlePx

    /**
     * Frames of the video before the kept part ([handlesBefore] 0), in it (1) or after it (2) are drawn
     * `(t - positionMs) × pxPerMs` plus this right of the playhead, on the given side of it.
     */
    fun frameShift(handlesBefore: Int, rightOfPlayhead: Boolean): Float =
        (handlesBefore - handlesBeforePlayhead) * handlePx + side(rightOfPlayhead)

    /**
     * The moment of [handle] when its inner edge is drawn [edgeX] right of the playhead, the other handle
     * staying put. Where the edge can't be, between its places on either side of the playhead, the handle
     * holds on to the playhead.
     */
    fun handleTimeAt(handle: TrimHandle, edgeX: Float): Float {
        // Where the edge is drawn at the playhead's moment with the handle left of the playhead, and right of it;
        // null on a side the handle can't be.
        val (leftEdge: Float?, rightEdge: Float?) = when (handle) {
            // Past the end of the kept part the start handle can't reach the playhead.
            TrimHandle.Start -> if (positionMs > endMs) -handlePx - gapPx to null else -gapPx to handlePx + gapPx
            // Before the start of the kept part the end handle can't reach the playhead.
            TrimHandle.End -> if (positionMs < startMs) null to handlePx + gapPx else -handlePx - gapPx to gapPx
        }
        return when {
            leftEdge != null && (rightEdge == null || edgeX <= leftEdge) -> positionMs + (edgeX - leftEdge) / pxPerMs
            rightEdge != null && (leftEdge == null || edgeX >= rightEdge) -> positionMs + (edgeX - rightEdge) / pxPerMs
            else -> positionMs
        }
    }

    private fun side(rightOfPlayhead: Boolean) = if (rightOfPlayhead) gapPx else -gapPx
}

/**
 * How fast the strip scrolls under a handle held near an edge of a bar [widthPx] wide, in pixels per
 * second; positive brings later moments in from the right. It grows from 0 at [edgePx] from the edge to
 * [maxSpeedPx] at the edge. A drag that starts closer to an edge than [edgePx], at [startX], scrolls only
 * once the finger goes on towards that edge: the band shrinks to where it started.
 */
internal fun autoScrollSpeed(fingerX: Float, startX: Float, widthPx: Float, edgePx: Float, maxSpeedPx: Float): Float {
    val leftBand = min(edgePx, startX)
    val rightBand = min(edgePx, widthPx - startX)
    return when {
        leftBand > 0f && fingerX < leftBand -> -maxSpeedPx * min(1f, (leftBand - fingerX) / leftBand)
        rightBand > 0f && fingerX > widthPx - rightBand -> maxSpeedPx * min(1f, (fingerX - (widthPx - rightBand)) / rightBand)
        else -> 0f
    }
}

/**
 * How many times faster the auto-scroll goes after the handle has been held at an edge for [heldSeconds]:
 * twice as fast every [doublingSeconds], up to [maxBoost]. A long video goes by in seconds, a short one
 * stays easy to aim in.
 */
internal fun autoScrollBoost(heldSeconds: Float, doublingSeconds: Float, maxBoost: Float): Float =
    min(2f.pow(heldSeconds / doublingSeconds), maxBoost)

/**
 * Opacity of what is drawn [x] px right of the playhead: full from [opaqueFromX] to [opaqueToX], the outer
 * edges of the handles, and fading out linearly over [fadePx] beyond them, where the frames fade.
 */
internal fun fadeAlpha(x: Float, opaqueFromX: Float, opaqueToX: Float, fadePx: Float): Float {
    val outsidePx = when {
        x < opaqueFromX -> opaqueFromX - x
        x > opaqueToX -> x - opaqueToX
        else -> return 1f
    }
    return (1f - outsidePx / fadePx).coerceIn(0f, 1f)
}

/** The whole seconds of a [durationMs] long video between [fromMs] and [toMs]; empty when there are none. */
internal fun secondsBetween(fromMs: Float, toMs: Float, durationMs: Float): IntRange {
    val first = ceil(max(fromMs, 0f) / MS_PER_SECOND).toInt()
    val last = floor(min(toMs, durationMs) / MS_PER_SECOND).toInt()
    return first..last
}

/** The scale names every other second, from 00:00; the ones between are dots. */
internal fun isLabelled(second: Int): Boolean = second % 2 == 0

/** `mm:ss`, the way the scale names a moment. Minutes go past 59 rather than adding hours. */
internal fun timeLabel(ms: Long): String {
    val seconds = max(0L, ms) / MS_PER_SECOND.toLong()
    return "${(seconds / 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** The frame a tile shows: the one covering the middle of the tile, [tileMiddleMs]. [count] must be positive. */
internal fun frameIndex(tileMiddleMs: Float, intervalMs: Long, count: Int): Int =
    (tileMiddleMs / intervalMs).toInt().coerceIn(0, count - 1)

private const val MS_PER_SECOND = 1000f
