package com.onthecrow.wallper.core.designsystem.component.trimbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.designsystem.theme.WallperTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * A timeline to trim a clip: the Material 3 range slider stretched tall. The strip of [frames] is the
 * track, the kept part sits in a frame with a handle in each rounded end, the playhead is the thumb, and
 * a scale of seconds runs above. Frames beyond a handle fade out over [fadeWidth].
 *
 * The playhead stays at the centre of the bar and the strip moves under it, following [position] or the
 * finger. The bar knows nothing of video: the caller plays it, shows the frames it is asked for and feeds
 * the position back.
 * - Dragging the strip scrubs within the kept part, and a fling carries on. Every moment goes to [onSeek].
 * - Dragging a handle reports the new part through [onTrimChange] and the handle's moment through [onSeek],
 *   so the caller can show that frame. Within [snapDistance] of the playhead the handle sticks to it with a
 *   haptic tick; held near an edge of the bar, it scrolls the strip, faster the longer it is held there.
 *   Once let go, the playhead moves to the handle: it glides there from inside the kept part and jumps
 *   from outside.
 * - [onDragStart] comes before any of these and [onDragEnd] once the strip is still again, with the playhead
 *   at the moment of the last [onSeek]; also when the bar leaves the composition mid-drag. A drag that
 *   starts while the strip still moves continues the same one.
 *
 * The bar takes the system's back gesture off its edges: a handle often sits right at one.
 *
 * @param position the moment the caller shows, in ms. The bar reads it while drawing, so a state read
 * inside redraws the bar without recomposing it. Ignored from [onDragStart] to [onDragEnd].
 * @param frames read while drawing too, so frames arriving one by one only redraw the bar.
 * @param minDurationMs the shortest part the handles keep, unless the whole video is shorter.
 */
@Composable
fun TrimBar(
    durationMs: Long,
    position: () -> Long,
    startMs: Long,
    endMs: Long,
    onTrimChange: (startMs: Long, endMs: Long) -> Unit,
    onSeek: (positionMs: Long) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    frames: () -> TrimBarFrames = { TrimBarFrames.Empty },
    colors: TrimBarColors = TrimBarDefaults.colors(),
    secondWidth: Dp = TrimBarDefaults.SecondWidth,
    snapDistance: Dp = TrimBarDefaults.SnapDistance,
    fadeWidth: Dp = TrimBarDefaults.FadeWidth,
    minDurationMs: Long = TrimBarDefaults.MIN_DURATION_MS,
) {
    val drag = remember { TrimBarDrag() }
    val haptics = LocalHapticFeedback.current
    val decay = rememberSplineBasedDecay<Float>()
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    // A gesture outlives recompositions: it reads the latest of these.
    val currentDurationMs by rememberUpdatedState(durationMs)
    val currentPosition by rememberUpdatedState(position)
    val currentTrim by rememberUpdatedState(Trim(startMs.toFloat(), endMs.toFloat()))
    val currentOnTrimChange by rememberUpdatedState(onTrimChange)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            .systemGestureExclusion()
            .pointerInput(drag, decay, secondWidth, snapDistance, minDurationMs) {
                val pxPerMs = secondWidth.toPx() / MS_PER_SECOND
                val snapPx = snapDistance.toPx()
                val touchRadiusPx = HandleTouchRadius.toPx()
                val handlePx = HandleZone.toPx()
                val gapPx = PlayheadHalfGap.toPx()
                val edgePx = AutoScrollEdge.toPx()
                val maxSpeedPx = AutoScrollMaxSpeed.toPx()

                fun finish() {
                    drag.active = false
                    drag.positionMs = null
                    // The caller's state again, also when it didn't take the handle where it was left.
                    drag.trim = null
                    currentOnDragEnd()
                }

                // Cancelled mid-drag, the gesture still ends: the caller would hold playback forever otherwise.
                try {
                    coroutineScope {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            drag.stopSettling()
                            val durationMsF = currentDurationMs.toFloat()
                            if (durationMsF <= 0f) return@awaitEachGesture
                            drag.trim = null
                            val trim = currentTrim
                            val centerX = size.width / 2f
                            var positionMs = drag.positionMs ?: currentPosition().toFloat()
                            val layout = TrimLayout(positionMs, trim.startMs, trim.endMs, pxPerMs, handlePx, gapPx)
                            val handle = handleAt(
                                offsetPx = down.position.x - centerX,
                                startOffsetPx = layout.startHandleLeft + handlePx / 2f,
                                endOffsetPx = layout.endHandleLeft + handlePx / 2f,
                                touchRadiusPx = touchRadiusPx,
                            )
                            var overSlop = 0f
                            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                overSlop = over
                            }
                            if (slop == null) {
                                // A tap only stops the strip.
                                if (drag.active) finish()
                                return@awaitEachGesture
                            }
                            if (!drag.active) {
                                drag.active = true
                                currentOnDragStart()
                            }

                            if (handle == null) {
                                positionMs = positionMs.coerceIn(trim.startMs, trim.endMs)
                                fun scrubBy(dragPx: Float) {
                                    positionMs = scrub(positionMs, dragPx, pxPerMs, trim.startMs, trim.endMs)
                                    drag.positionMs = positionMs
                                    currentOnSeek(positionMs.roundToLong())
                                }
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(slop.uptimeMillis, slop.position)
                                scrubBy(overSlop)
                                horizontalDrag(slop.id) { change ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    scrubBy(change.positionChange().x)
                                    change.consume()
                                }
                                val velocity = velocityTracker.calculateVelocity().x
                                drag.settling = launch {
                                    val strip = Animatable(positionMs * pxPerMs)
                                    strip.updateBounds(trim.startMs * pxPerMs, trim.endMs * pxPerMs)
                                    // The strip moves with the finger: a fling to the right goes back in time.
                                    strip.animateDecay(-velocity, decay) {
                                        drag.positionMs = value / pxPerMs
                                        currentOnSeek((value / pxPerMs).roundToLong())
                                    }
                                    finish()
                                }
                            } else {
                                var handleMs = if (handle == TrimHandle.Start) trim.startMs else trim.endMs
                                // The finger keeps the hold it took on the handle, whose moment is its inner edge.
                                val edgeX = if (handle == TrimHandle.Start) layout.startHandleRight else layout.endHandleLeft
                                val grabPx = edgeX - (down.position.x - centerX)
                                drag.positionMs = positionMs
                                drag.fingerX = slop.position.x
                                drag.snapped = false
                                // Its frame shows from the start, also for a handle that can't move that way.
                                currentOnSeek(handleMs.roundToLong())

                                fun moveTo(atPositionMs: Float): Boolean {
                                    val move = moveHandle(
                                        handle = handle,
                                        rawMs = TrimLayout(atPositionMs, trim.startMs, trim.endMs, pxPerMs, handlePx, gapPx)
                                            .handleTimeAt(handle, drag.fingerX - centerX + grabPx),
                                        startMs = trim.startMs,
                                        endMs = trim.endMs,
                                        durationMs = durationMsF,
                                        positionMs = atPositionMs,
                                        minDurationMs = minDurationMs.toFloat(),
                                        snapPx = snapPx,
                                        pxPerMs = pxPerMs,
                                    )
                                    if (move.snapped && !drag.snapped) {
                                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                    }
                                    drag.snapped = move.snapped
                                    if (move.timeMs == handleMs) return false
                                    handleMs = move.timeMs
                                    val moved = when (handle) {
                                        TrimHandle.Start -> trim.copy(startMs = handleMs)
                                        TrimHandle.End -> trim.copy(endMs = handleMs)
                                    }
                                    drag.trim = moved
                                    currentOnTrimChange(moved.startMs.roundToLong(), moved.endMs.roundToLong())
                                    currentOnSeek(handleMs.roundToLong())
                                    return true
                                }

                                moveTo(positionMs)
                                // Held near an edge, the handle scrolls the strip for as long as it can follow.
                                val autoScroll = launch {
                                    var lastFrameNanos = withFrameNanos { it }
                                    var heldSeconds = 0f
                                    while (true) {
                                        val frameNanos = withFrameNanos { it }
                                        val seconds = (frameNanos - lastFrameNanos) / NANOS_PER_SECOND
                                        lastFrameNanos = frameNanos
                                        val speedPx = autoScrollSpeed(drag.fingerX, down.position.x, size.width.toFloat(), edgePx, maxSpeedPx)
                                        heldSeconds = if (speedPx == 0f) 0f else heldSeconds + seconds
                                        val boost = autoScrollBoost(heldSeconds, AUTO_SCROLL_DOUBLING_S, AUTO_SCROLL_MAX_BOOST)
                                        val scrolledMs = (positionMs + speedPx * boost * seconds / pxPerMs).coerceIn(0f, durationMsF)
                                        if (scrolledMs != positionMs && moveTo(scrolledMs)) {
                                            positionMs = scrolledMs
                                            drag.positionMs = scrolledMs
                                        }
                                    }
                                }
                                horizontalDrag(slop.id) { change ->
                                    drag.fingerX = change.position.x
                                    moveTo(positionMs)
                                    change.consume()
                                }
                                autoScroll.cancel()

                                val kept = drag.trim ?: trim
                                if (positionMs in kept.startMs..kept.endMs) {
                                    val targetMs = handleMs
                                    drag.settleTarget = targetMs
                                    drag.settling = launch {
                                        animate(positionMs, targetMs, animationSpec = tween(SETTLE_MS, easing = FastOutSlowInEasing)) { value, _ ->
                                            drag.positionMs = value
                                        }
                                        drag.settleTarget = null
                                        finish()
                                    }
                                } else {
                                    drag.positionMs = handleMs
                                    finish()
                                }
                            }
                        }
                    }
                } finally {
                    if (drag.active) finish()
                }
            }
            .drawBehind {
                if (durationMs <= 0) return@drawBehind
                drawTrimBar(
                    durationMs = durationMs.toFloat(),
                    positionMs = drag.positionMs ?: position().toFloat(),
                    trim = drag.trim ?: Trim(startMs.toFloat(), endMs.toFloat()),
                    frames = frames(),
                    colors = colors,
                    pxPerMs = secondWidth.toPx() / MS_PER_SECOND,
                    fadePx = fadeWidth.toPx(),
                    textMeasurer = textMeasurer,
                    labelStyle = labelStyle,
                )
            },
    )
}

/** The kept part, in ms. */
private data class Trim(val startMs: Float, val endMs: Float)

/** What the user is doing with a [TrimBar]. While it lasts, the bar draws this rather than its parameters. */
private class TrimBarDrag {
    /** The moment under the playhead while the strip is dragged, flung or settling; null to follow the caller. */
    var positionMs: Float? by mutableStateOf(null)

    /** The kept part from a handle drag to the end of the drag, ahead of the caller's state; null to follow the caller. */
    var trim: Trim? by mutableStateOf(null)

    /** The finger on the handle; the auto-scroll reads it every frame. */
    var fingerX = 0f

    /** Whether the dragged handle sits on the playhead: it ticks once on getting there. */
    var snapped = false

    /** Between onDragStart and onDragEnd. */
    var active = false

    /** The fling or the glide after a drag. */
    var settling: Job? = null

    /** Where the glide ends: a new touch skips straight there. */
    var settleTarget: Float? = null

    fun stopSettling() {
        settling?.cancel()
        settling = null
        settleTarget?.let { positionMs = it }
        settleTarget = null
    }
}

/** What the strip shows and where, for drawing frames into any part of it. */
private class FrameStrip(
    val frames: TrimBarFrames,
    val durationMs: Float,
    val positionMs: Float,
    val trim: Trim,
    val layout: TrimLayout,
    val pxPerMs: Float,
    val centerX: Float,
    val top: Float,
    val bottom: Float,
    val placeholder: Color,
)

private fun DrawScope.drawTrimBar(
    durationMs: Float,
    positionMs: Float,
    trim: Trim,
    frames: TrimBarFrames,
    colors: TrimBarColors,
    pxPerMs: Float,
    fadePx: Float,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    val centerX = size.width / 2f

    val layout = TrimLayout(positionMs, trim.startMs, trim.endMs, pxPerMs, HandleZone.toPx(), PlayheadHalfGap.toPx())

    // The scale: even seconds named, odd ones dotted, fading out where the frames do. It keeps to time alone,
    // unparted by the playhead and the handles, so the playhead's moment is right above it.
    val scaleY = ScaleHeight.toPx() / 2f
    val viewHalfMs = centerX / pxPerMs + MS_PER_SECOND // and a label sticking in from beyond an edge
    for (second in secondsBetween(positionMs - viewHalfMs, positionMs + viewHalfMs, durationMs)) {
        val timeMs = second * MS_PER_SECOND
        val offsetX = offsetOf(timeMs, positionMs, pxPerMs)
        val alpha = fadeAlpha(offsetX, layout.startHandleLeft, layout.endHandleRight, fadePx)
        if (alpha == 0f) continue
        val x = centerX + offsetX
        if (isLabelled(second)) {
            val label = textMeasurer.measure(timeLabel(timeMs.toLong()), labelStyle)
            drawText(
                textLayoutResult = label,
                color = colors.scale,
                topLeft = Offset(x - label.size.width / 2f, scaleY - label.size.height / 2f),
                alpha = alpha,
            )
        } else {
            drawCircle(colors.scale, radius = DotRadius.toPx(), center = Offset(x, scaleY), alpha = alpha)
        }
    }

    val trackTop = TrackTop.toPx()
    val trackBottom = trackTop + TrackHeight.toPx()
    val border = Border.toPx()
    val strip = FrameStrip(
        frames = frames,
        durationMs = durationMs,
        positionMs = positionMs,
        trim = trim,
        layout = layout,
        pxPerMs = pxPerMs,
        centerX = centerX,
        top = trackTop + border,
        bottom = trackBottom - border,
        placeholder = colors.framePlaceholder,
    )
    val startHandleLeft = centerX + layout.startHandleLeft
    val endHandleRight = centerX + layout.endHandleRight

    // Beyond the handles the frames go on, under the handle and outwards, and fade out linearly.
    if (trim.startMs > 0f) {
        drawOuterFrames(strip, TrimHandle.Start, centerX + layout.startHandleLeft, centerX + layout.startHandleRight, fadePx)
    }
    if (trim.endMs < durationMs) {
        drawOuterFrames(strip, TrimHandle.End, centerX + layout.endHandleLeft, endHandleRight, fadePx)
    }

    // The kept part: the track, with a handle in each rounded end, parted by the playhead when it is inside.
    if (positionMs in trim.startMs..trim.endMs) {
        drawTrackPiece(strip, startHandleLeft, centerX - layout.gapPx, trackTop, trackBottom, colors.track, roundLeft = true, roundRight = false)
        drawTrackPiece(strip, centerX + layout.gapPx, endHandleRight, trackTop, trackBottom, colors.track, roundLeft = false, roundRight = true)
    } else {
        drawTrackPiece(strip, startHandleLeft, endHandleRight, trackTop, trackBottom, colors.track, roundLeft = true, roundRight = true)
    }

    val trackCenterY = (trackTop + trackBottom) / 2f
    fun drawHandle(handleLeft: Float) {
        val width = HandleBarWidth.toPx()
        val height = HandleBarHeight.toPx()
        drawRoundRect(
            color = colors.handle,
            topLeft = Offset(handleLeft + (layout.handlePx - width) / 2f, trackCenterY - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
    drawHandle(startHandleLeft)
    drawHandle(centerX + layout.endHandleLeft)

    val playheadWidth = PlayheadWidth.toPx()
    val overhang = PlayheadOverhang.toPx()
    drawRoundRect(
        color = colors.playhead,
        topLeft = Offset(centerX - playheadWidth / 2f, trackTop - overhang),
        size = Size(playheadWidth, trackBottom - trackTop + 2 * overhang),
        cornerRadius = CornerRadius(playheadWidth / 2f),
    )
}

/**
 * A piece of the track from [left] to [right] with the kept frames inside. A rounded end holds a handle;
 * an end at the playhead is narrow, with small corners.
 */
private fun DrawScope.drawTrackPiece(
    strip: FrameStrip,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    color: Color,
    roundLeft: Boolean,
    roundRight: Boolean,
) {
    if (right <= left) return
    val outer = CornerRadius(TrackCorner.toPx())
    val inner = CornerRadius(TrackInnerCorner.toPx())
    drawPath(roundRect(left, top, right, bottom, if (roundLeft) outer else inner, if (roundRight) outer else inner), color)

    val handle = strip.layout.handlePx
    val border = Border.toPx()
    val framesLeft = left + if (roundLeft) handle else border
    val framesRight = right - if (roundRight) handle else border
    if (framesRight <= framesLeft) return
    val frameOuter = CornerRadius(FrameCorner.toPx())
    val frameInner = CornerRadius(FrameInnerCorner.toPx())
    val window = roundRect(
        framesLeft,
        strip.top,
        framesRight,
        strip.bottom,
        if (roundLeft) frameOuter else frameInner,
        if (roundRight) frameOuter else frameInner,
    )
    clipPath(window) {
        drawVideoPart(strip, strip.trim.startMs, strip.trim.endMs, handlesBefore = 1, left = framesLeft, right = framesRight)
    }
}

private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, leftCorner: CornerRadius, rightCorner: CornerRadius) =
    Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = leftCorner,
                topRightCornerRadius = rightCorner,
                bottomRightCornerRadius = rightCorner,
                bottomLeftCornerRadius = leftCorner,
            ),
        )
    }

/**
 * The video beyond the [handle] drawn from [handleLeft] to [handleRight]: it carries on under the handle,
 * so the handle's rounded corners show frames rather than notches, and fades out over [fadePx] past it.
 */
private fun DrawScope.drawOuterFrames(strip: FrameStrip, handle: TrimHandle, handleLeft: Float, handleRight: Float, fadePx: Float) =
    with(strip) {
        val handleMs = layout.handlePx / pxPerMs
        // The frames next to a handle are on its side of the playhead; the ones under it go on the same way.
        val (opaqueX, clearX) = when (handle) {
            TrimHandle.Start -> handleLeft to handleLeft - fadePx
            TrimHandle.End -> handleRight to handleRight + fadePx
        }
        // Whole pixels, and the mask over all of the layer: a mask edge mid-pixel would leave that column unfaded.
        val bounds = Rect(floor(min(clearX, handleLeft)), floor(top), ceil(max(clearX, handleRight)), ceil(bottom))
        drawContext.canvas.saveLayer(bounds, Paint())
        when (handle) {
            TrimHandle.Start -> {
                drawVideoPart(strip, 0f, trim.startMs, handlesBefore = 0, left = bounds.left, right = handleLeft)
                val shift = layout.frameShift(handlesBefore = 0, rightOfPlayhead = positionMs < trim.startMs)
                drawFrames(strip, trim.startMs, trim.startMs + handleMs, shift, handleLeft, handleRight)
            }
            TrimHandle.End -> {
                val shift = layout.frameShift(handlesBefore = 2, rightOfPlayhead = positionMs <= trim.endMs)
                drawFrames(strip, trim.endMs - handleMs, trim.endMs, shift, handleLeft, handleRight)
                drawVideoPart(strip, trim.endMs, durationMs, handlesBefore = 2, left = handleRight, right = bounds.right)
            }
        }
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = opaqueX, endX = clearX),
            topLeft = bounds.topLeft,
            size = bounds.size,
            blendMode = BlendMode.DstIn,
        )
        drawContext.canvas.restore()
    }

/**
 * The frames of [fromMs, toMs] within [left, right]: a part of the video with [handlesBefore] handles before
 * it (0 before the kept part, 1 in it, 2 after it). Each side of the playhead goes to its place, the gap
 * between them stays empty.
 */
private fun DrawScope.drawVideoPart(
    strip: FrameStrip,
    fromMs: Float,
    toMs: Float,
    handlesBefore: Int,
    left: Float,
    right: Float,
) = with(strip) {
    if (fromMs < positionMs) {
        val shift = layout.frameShift(handlesBefore, rightOfPlayhead = false)
        drawFrames(strip, fromMs, min(toMs, positionMs), shift, left, min(right, centerX - layout.gapPx))
    }
    if (toMs > positionMs) {
        val shift = layout.frameShift(handlesBefore, rightOfPlayhead = true)
        drawFrames(strip, max(fromMs, positionMs), toMs, shift, max(left, centerX + layout.gapPx), right)
    }
}

/**
 * Tiles [left, right] with the frames of [fromMs, toMs], each moment `t` drawn `(t - positionMs) × pxPerMs +
 * shiftPx` right of the playhead. A tile has the frames' aspect ratio and shows the frame that covers its middle.
 */
private fun DrawScope.drawFrames(
    strip: FrameStrip,
    fromMs: Float,
    toMs: Float,
    shiftPx: Float,
    left: Float,
    right: Float,
) = with(strip) {
    fun x(timeMs: Float) = centerX + (timeMs - positionMs) * pxPerMs + shiftPx
    val visibleLeft = max(left, x(fromMs))
    val visibleRight = min(right, x(toMs))
    if (visibleRight <= visibleLeft) return@with
    clipRect(visibleLeft, top, visibleRight, bottom) {
        drawRect(placeholder, Offset(visibleLeft, top), Size(visibleRight - visibleLeft, bottom - top))
        if (frames.images.isEmpty()) return@clipRect
        val tileMs = (bottom - top) * frames.aspectRatio / pxPerMs
        val frameCount = ceil(durationMs / frames.intervalMs).toInt()
        val firstMs = positionMs + (visibleLeft - centerX - shiftPx) / pxPerMs
        val lastMs = positionMs + (visibleRight - centerX - shiftPx) / pxPerMs
        val dstTop = top.roundToInt()
        val dstHeight = bottom.roundToInt() - dstTop
        for (tile in floor(firstMs / tileMs).toInt() until ceil(lastMs / tileMs).toInt()) {
            val image = frames.images.getOrNull(frameIndex((tile + 0.5f) * tileMs, frames.intervalMs, frameCount)) ?: continue
            // Both edges from the same formula, so neighbouring tiles neither overlap nor leave a gap.
            val dstLeft = x(tile * tileMs).roundToInt()
            val dstRight = x((tile + 1) * tileMs).roundToInt()
            drawImageCropped(image, IntOffset(dstLeft, dstTop), IntSize(dstRight - dstLeft, dstHeight))
        }
    }
}

/** Draws the middle of [image] that has the shape of the destination. */
private fun DrawScope.drawImageCropped(image: ImageBitmap, dstOffset: IntOffset, dstSize: IntSize) {
    if (dstSize.width <= 0 || dstSize.height <= 0) return
    val dstAspect = dstSize.width.toFloat() / dstSize.height
    val srcSize = if (image.width.toFloat() / image.height > dstAspect) {
        IntSize((image.height * dstAspect).roundToInt().coerceIn(1, image.width), image.height)
    } else {
        IntSize(image.width, (image.width / dstAspect).roundToInt().coerceIn(1, image.height))
    }
    val srcOffset = IntOffset((image.width - srcSize.width) / 2, (image.height - srcSize.height) / 2)
    drawImage(image, srcOffset, srcSize, dstOffset, dstSize)
}

private val ScaleHeight = 16.dp
private val ScaleGap = 4.dp
private val DotRadius = 1.5.dp
private val Border = 4.dp
private val TrackHeight = TrimBarDefaults.FrameHeight + Border * 2
private val TrackCorner = 16.dp
private val TrackInnerCorner = 6.dp
private val FrameCorner = 4.dp
private val FrameInnerCorner = 2.dp
private val HandleZone = 16.dp
private val HandleBarWidth = 4.dp
private val HandleBarHeight = 24.dp
private val HandleTouchRadius = 24.dp
private val PlayheadWidth = 4.dp

/** From the middle of the playhead to the frames on either side. */
private val PlayheadHalfGap = PlayheadWidth / 2 + 6.dp
private val PlayheadOverhang = 6.dp
private val TrackTop = ScaleHeight + ScaleGap + PlayheadOverhang
private val BarHeight = TrackTop + TrackHeight + PlayheadOverhang
private val AutoScrollEdge = 48.dp

/** Per second, at the very edge, before the boost of a long hold. */
private val AutoScrollMaxSpeed = 512.dp
private const val AUTO_SCROLL_DOUBLING_S = 1f

/** 32 × 512 dp a second: some four minutes of video a second at the default scale. */
private const val AUTO_SCROLL_MAX_BOOST = 32f
private const val SETTLE_MS = 250
private const val MS_PER_SECOND = 1_000f
private const val NANOS_PER_SECOND = 1_000_000_000f

@Preview(widthDp = 400)
@Composable
private fun TrimBarPreview() {
    var positionMs by remember { mutableLongStateOf(3_000L) }
    var startMs by remember { mutableLongStateOf(1_000L) }
    var endMs by remember { mutableLongStateOf(9_000L) }
    val frames = remember { previewFrames(durationMs = 12_000) }
    WallperTheme(dynamicColor = false) {
        TrimBar(
            durationMs = 12_000,
            position = { positionMs },
            startMs = startMs,
            endMs = endMs,
            onTrimChange = { start, end ->
                startMs = start
                endMs = end
            },
            onSeek = { positionMs = it },
            onDragStart = {},
            onDragEnd = {},
            frames = { frames },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp),
        )
    }
}

/** A rainbow of frames: each one tells where it belongs. */
private fun previewFrames(durationMs: Long): TrimBarFrames {
    val aspectRatio = 9f / 16f
    val intervalMs = TrimBarDefaults.frameIntervalMs(aspectRatio)
    val count = ((durationMs + intervalMs - 1) / intervalMs).toInt()
    val paint = Paint()
    val images = List(count) { i ->
        ImageBitmap(9, 16).also { image ->
            paint.color = Color.hsv(360f * i / count, 0.45f, 0.85f)
            Canvas(image).drawRect(0f, 0f, 9f, 16f, paint)
        }
    }
    return TrimBarFrames(images, intervalMs, aspectRatio)
}
