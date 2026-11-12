package com.onthecrow.wallper.editor.crop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The crop: a still phone frame with the screen's proportions, as large as fits inside [framePadding],
 * and [picture] under it, which gestures drag, pinch and turn. When the fingers let go the picture springs back
 * to cover the whole frame. Around the frame the picture is dimmed and fades into the background; while the
 * fingers are down a rule-of-thirds grid shows in the frame, and dashed yellow lines through its middle show
 * the snaps that hold the picture: across for level or centred up and down, up and down for centred across.
 * [onSnap] is called each time the picture snaps into one of them.
 *
 * [picture] fills the media's upright proportions; [state] places it.
 */
@Composable
internal fun CropEditor(
    state: CropState,
    onSnap: () -> Unit,
    modifier: Modifier = Modifier,
    framePadding: PaddingValues = CropEditorDefaults.FramePadding,
    picture: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.background
    val frameColor = MaterialTheme.colorScheme.primary
    val gridAlpha by animateFloatAsState(
        targetValue = if (state.isInteracting) 1f else 0f,
        animationSpec = tween(CropEditorDefaults.GRID_FADE_MS),
        label = "crop grid",
    )
    val guides = state.guides
    val horizontalGuideAlpha by animateFloatAsState(
        targetValue = if (guides.level || guides.centerY) 1f else 0f,
        animationSpec = tween(CropEditorDefaults.GUIDE_FADE_MS),
        label = "crop horizontal guide",
    )
    val verticalGuideAlpha by animateFloatAsState(
        targetValue = if (guides.centerX) 1f else 0f,
        animationSpec = tween(CropEditorDefaults.GUIDE_FADE_MS),
        label = "crop vertical guide",
    )
    val currentOnSnap by rememberUpdatedState(onSnap)

    BoxWithConstraints(modifier.clipToBounds()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val bounds = remember(areaWidth, areaHeight, framePadding, density, layoutDirection, state.screenAspectRatio) {
            with(density) {
                FrameBounds.fit(
                    area = Size(areaWidth, areaHeight),
                    padding = Rect(
                        left = framePadding.calculateStartPadding(layoutDirection).toPx(),
                        top = framePadding.calculateTopPadding().toPx(),
                        right = framePadding.calculateEndPadding(layoutDirection).toPx(),
                        bottom = framePadding.calculateBottomPadding().toPx(),
                    ),
                    border = PhoneFrameDefaults.BorderWidth.toPx(),
                    aspectRatio = state.screenAspectRatio,
                )
            }
        }
        val screen = bounds.screen
        SideEffect { state.onFrameSize(screen.width, screen.height) }

        val frameCenter by rememberUpdatedState(screen.center)
        val gestures = Modifier.pointerInput(state) {
            detectCropGestures(
                onStart = state::onGestureStart,
                onGesture = { centroid, pan, zoom, rotation ->
                    val fromCenter = centroid - frameCenter
                    if (state.onGesture(fromCenter.x, fromCenter.y, pan.x, pan.y, zoom, rotation)) currentOnSnap()
                },
                onEnd = { state.onGestureEnd(scope) },
            )
        }

        // The picture is laid out once at the scale that just covers the frame; gestures only change its layer.
        val media = state.mediaSize
        val baseScale = if (media.width > 0 && media.height > 0) {
            max(screen.width / media.width, screen.height / media.height)
        } else {
            0f
        }
        val pictureWidth = (media.width * baseScale).roundToInt()
        val pictureHeight = (media.height * baseScale).roundToInt()
        val pictureLeft = (screen.center.x - pictureWidth / 2f).roundToInt()
        val pictureTop = (screen.center.y - pictureHeight / 2f).roundToInt()
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .placedAt(pictureLeft, pictureTop, pictureWidth, pictureHeight)
                    .graphicsLayer {
                        val placement = state.placement
                        if (placement == null || baseScale <= 0f) {
                            alpha = 0f
                        } else {
                            val scale = placement.scale / baseScale
                            scaleX = scale
                            scaleY = scale
                            rotationZ = placement.rotation
                            // The rounding of the layout put its centre a little off the frame's.
                            translationX = placement.offsetX + screen.center.x - (pictureLeft + pictureWidth / 2f)
                            translationY = placement.offsetY + screen.center.y - (pictureTop + pictureHeight / 2f)
                        }
                    }
            ) {
                picture()
            }

            // Above the picture, so a video's view never sees the fingers.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .then(gestures)
            ) {
                val screenCorner = PhoneFrameDefaults.ScreenCornerRadius.toPx()
                dimAround(screen, screenCorner, background.copy(alpha = CropEditorDefaults.DIM_ALPHA))
                fadeAround(bounds.body, background)
                if (gridAlpha > 0f) {
                    drawThirds(screen, Color.White.copy(alpha = CropEditorDefaults.GRID_ALPHA * gridAlpha))
                }
                if (horizontalGuideAlpha > 0f) {
                    drawGuide(screen, horizontal = true, CropEditorDefaults.GuideColor.copy(alpha = horizontalGuideAlpha))
                }
                if (verticalGuideAlpha > 0f) {
                    drawGuide(screen, horizontal = false, CropEditorDefaults.GuideColor.copy(alpha = verticalGuideAlpha))
                }
            }

            PhoneFrame(
                modifier = Modifier.placedAt(
                    left = bounds.body.left.roundToInt(),
                    top = bounds.body.top.roundToInt(),
                    width = bounds.body.width.roundToInt(),
                    height = bounds.body.height.roundToInt(),
                ),
                color = frameColor,
            )
        }
    }
}

internal object CropEditorDefaults {
    val FramePadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)

    /** How much the picture is dimmed around the frame. */
    const val DIM_ALPHA = 0.6f
    const val GRID_ALPHA = 0.7f
    const val GRID_FADE_MS = 200

    /** The lines that show a snap. */
    val GuideColor = Color(0xFFFFD60A)
    const val GUIDE_FADE_MS = 150
}

/** The phone's [body] (its outer edge) and its [screen], the opening the crop shows, in pixels of the area. */
internal data class FrameBounds(val body: Rect, val screen: Rect) {
    companion object {
        /**
         * The largest body that fits [area] inside [padding] (each side's inset), centred, around a screen of
         * [aspectRatio] (width / height) with a [border] on every side.
         */
        fun fit(area: Size, padding: Rect, border: Float, aspectRatio: Float): FrameBounds {
            val zoneWidth = area.width - padding.left - padding.right
            val zoneHeight = area.height - padding.top - padding.bottom
            val screenHeight = min(zoneHeight - 2 * border, (zoneWidth - 2 * border) / aspectRatio).coerceAtLeast(0f)
            val screenWidth = screenHeight * aspectRatio
            val bodyWidth = screenWidth + 2 * border
            val bodyHeight = screenHeight + 2 * border
            val body = Rect(
                offset = Offset(padding.left + (zoneWidth - bodyWidth) / 2, padding.top + (zoneHeight - bodyHeight) / 2),
                size = Size(bodyWidth, bodyHeight),
            )
            return FrameBounds(body = body, screen = body.deflate(border))
        }
    }
}

/** Lays the content out at [width] x [height], placed at [left], [top] of a parent it fills. */
private fun Modifier.placedAt(left: Int, top: Int, width: Int, height: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(0), height.coerceAtLeast(0)))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left, top) }
}

/** Covers everything but the [screen] with [color]. */
private fun DrawScope.dimAround(screen: Rect, cornerRadius: Float, color: Color) {
    val opening = Path().apply { addRoundRect(RoundRect(screen, CornerRadius(cornerRadius))) }
    clipPath(opening, ClipOp.Difference) { drawRect(color) }
}

/** Fades into [color] from each side of [body] to the same edge of the area. */
private fun DrawScope.fadeAround(body: Rect, color: Color) {
    val clear = color.copy(alpha = 0f)
    if (body.top > 0f) {
        drawRect(
            brush = Brush.verticalGradient(0f to color, 1f to clear, startY = 0f, endY = body.top),
            size = Size(size.width, body.top),
        )
    }
    if (body.bottom < size.height) {
        drawRect(
            brush = Brush.verticalGradient(0f to clear, 1f to color, startY = body.bottom, endY = size.height),
            topLeft = Offset(0f, body.bottom),
            size = Size(size.width, size.height - body.bottom),
        )
    }
    if (body.left > 0f) {
        drawRect(
            brush = Brush.horizontalGradient(0f to color, 1f to clear, startX = 0f, endX = body.left),
            size = Size(body.left, size.height),
        )
    }
    if (body.right < size.width) {
        drawRect(
            brush = Brush.horizontalGradient(0f to clear, 1f to color, startX = body.right, endX = size.width),
            topLeft = Offset(body.right, 0f),
            size = Size(size.width - body.right, size.height),
        )
    }
}

/** A dashed line through the middle of [screen], across it or up and down. */
private fun DrawScope.drawGuide(screen: Rect, horizontal: Boolean, color: Color) {
    val center = screen.center
    drawLine(
        color = color,
        start = if (horizontal) Offset(screen.left, center.y) else Offset(center.x, screen.top),
        end = if (horizontal) Offset(screen.right, center.y) else Offset(center.x, screen.bottom),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
    )
}

/** Two lines each way across [screen], dividing it in thirds. */
private fun DrawScope.drawThirds(screen: Rect, color: Color) {
    val stroke = 1.dp.toPx()
    for (i in 1..2) {
        val x = screen.left + screen.width * i / 3
        val y = screen.top + screen.height * i / 3
        drawLine(color, Offset(x, screen.top), Offset(x, screen.bottom), stroke)
        drawLine(color, Offset(screen.left, y), Offset(screen.right, y), stroke)
    }
}
