package com.onthecrow.wallper.wallpapers.preview

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.model.SeriesTimeline
import com.onthecrow.wallper.core.model.SeriesTimeline.Moment
import com.onthecrow.wallper.wallpapers.ui.R
import kotlinx.coroutines.CancellationException

/**
 * The round of [state]'s preview as a bar, the transitions marked on it as parts of their own, and a thumb
 * where the preview is. A finger anywhere on it stops the preview on the moment under it and moves it along
 * as it moves; letting go plays on from there. Below, what is on screen: a wallpaper of the series, or the
 * transition from one into the next.
 *
 * The bar reads the position only while drawing, so following playback draws it again and recomposes
 * nothing; the line below changes only with the wallpaper or the transition. The bar is kept from the
 * system's edge gestures: a finger there would go back instead.
 */
@Composable
internal fun SeriesScrubBar(state: SeriesPreviewState, modifier: Modifier = Modifier) {
    val timeline = state.preview?.timeline
    val currentState by rememberUpdatedState(state)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val transitionColor = MaterialTheme.colorScheme.tertiary
    val thumbColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .systemGestureExclusion()
                .pointerInput(timeline) {
                    val round = timeline ?: return@pointerInput
                    val inset = ThumbRadiusScrubbing.toPx()
                    fun positionAt(x: Float) = ScrubBarMath.positionAt(x, inset, size.width.toFloat(), round.durationMs)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        currentState.startScrubbing(positionAt(down.position.x))
                        try {
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { change ->
                                    if (change.pressed) currentState.scrubTo(positionAt(change.position.x))
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } catch (cancelled: CancellationException) {
                            currentState.stopScrubbing()
                            throw cancelled
                        }
                        currentState.stopScrubbing()
                    }
                }
                .drawBehind {
                    val inset = ThumbRadiusScrubbing.toPx()
                    val trackHeight = TrackHeight.toPx()
                    val top = (size.height - trackHeight) / 2
                    val width = size.width - 2 * inset
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(inset, top),
                        size = Size(width, trackHeight),
                        cornerRadius = CornerRadius(trackHeight / 2),
                    )
                    val round = timeline ?: return@drawBehind
                    fun xOf(positionMs: Long) = inset + width * positionMs / round.durationMs
                    if (round.blendMs > 0) {
                        repeat(round.slideCount) { index ->
                            val start = round.transitionStartOf(index)
                            drawRect(
                                color = transitionColor,
                                topLeft = Offset(xOf(start), top),
                                size = Size(xOf(start + round.blendMs) - xOf(start), trackHeight),
                            )
                        }
                    } else {
                        // Cuts: a notch where each slide gives way to the next.
                        (1 until round.slideCount).forEach { index ->
                            val x = xOf(round.holdStartOf(index))
                            drawLine(transitionColor, Offset(x, top), Offset(x, top + trackHeight), strokeWidth = NotchWidth.toPx())
                        }
                    }
                    val radius = if (currentState.isScrubbing) ThumbRadiusScrubbing else ThumbRadius
                    drawCircle(thumbColor, radius.toPx(), Offset(xOf(currentState.positionMs), size.height / 2))
                },
        )
        MomentLine(state, timeline)
    }
}

/** What is on screen: a wallpaper of the series, or the transition between two. */
@Composable
private fun MomentLine(state: SeriesPreviewState, timeline: SeriesTimeline?) {
    // Only the wallpaper or the transition, not the time in it: the line changes a few times a round.
    val shown by remember(state, timeline) {
        derivedStateOf {
            when (val moment = timeline?.at(state.positionMs)) {
                null -> null
                is Moment.Slide -> moment.index to null
                is Moment.Transition -> moment.from to moment.to
            }
        }
    }
    val (from, to) = shown ?: return
    Text(
        text = if (to == null) {
            stringResource(R.string.preview_moment_wallpaper, from + 1, timeline?.slideCount ?: 0)
        } else {
            stringResource(R.string.preview_moment_transition, from + 1, to + 1)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Where on the bar a finger points. Free of Android types: tested on the JVM. */
internal object ScrubBarMath {
    /**
     * The position under [x] on a bar [width] wide whose round of [durationMs] runs between insets of
     * [inset] at each end; past an end it is that end. Never the round's end itself, which is its start again.
     */
    fun positionAt(x: Float, inset: Float, width: Float, durationMs: Long): Long {
        val track = width - 2 * inset
        if (track <= 0f || durationMs <= 0) return 0
        val fraction = ((x - inset) / track).coerceIn(0f, 1f)
        return (fraction * durationMs).toLong().coerceAtMost(durationMs - 1)
    }
}

private val BarHeight = 36.dp
private val TrackHeight = 6.dp
private val ThumbRadius = 8.dp
private val ThumbRadiusScrubbing = 11.dp
private val NotchWidth = 2.dp
