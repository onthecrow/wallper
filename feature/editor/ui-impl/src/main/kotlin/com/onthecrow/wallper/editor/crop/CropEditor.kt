package com.onthecrow.wallper.editor.crop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.onthecrow.wallper.core.designsystem.component.phoneframe.FrameBounds
import com.onthecrow.wallper.core.designsystem.component.phoneframe.PhoneFrame
import com.onthecrow.wallper.core.designsystem.component.phoneframe.PhoneFrameDefaults
import com.onthecrow.wallper.core.designsystem.component.phoneframe.placedAt
import com.onthecrow.wallper.core.designsystem.component.phoneframe.rememberScreenShape
import com.onthecrow.wallper.core.designsystem.component.phoneframe.screenCornerRadius
import com.onthecrow.wallper.core.designsystem.component.phoneframe.toRect
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The crop: a still phone frame with the screen's proportions, as large as fits inside [framePadding],
 * and [picture] under it, which gestures drag, pinch and turn. When the fingers let go the picture springs back
 * to cover the whole frame. Around the frame the picture is dimmed and fades into the background; while the
 * fingers are down a rule-of-thirds grid shows in the frame, and dashed yellow lines through its middle show
 * the snaps that hold the picture: across for level or centred up and down, up and down for centred across.
 * [onSnap] is called each time the picture snaps into one of them.
 *
 * Turned ([FramingState.isTurned]) the phone lies across, scaled as a whole to fit, and the picture lies under
 * it where the turned crop has it: the frame turns a quarter and the picture moves from one crop to the other
 * in one animation ([CropEditorDefaults.TURN_MS]), drawn only, nothing laid out again. Fingers do nothing
 * meanwhile.
 *
 * [picture] fills the media's upright proportions; [state] places it.
 */
@Composable
internal fun CropEditor(
    state: FramingState,
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
    // 0 shows the base crop, 1 the turned one; the frame and the picture go the way between them together.
    val turnProgress by animateFloatAsState(
        targetValue = if (state.isTurned) 1f else 0f,
        animationSpec = tween(CropEditorDefaults.TURN_MS, easing = FastOutSlowInEasing),
        label = "crop turn",
    )
    val isResting = turnProgress == 0f || turnProgress == 1f
    val active = state.active
    val gridAlpha by animateFloatAsState(
        targetValue = if (active.isInteracting) 1f else 0f,
        animationSpec = tween(CropEditorDefaults.GRID_FADE_MS),
        label = "crop grid",
    )
    val guides = if (isResting) active.guides else Snaps.NONE
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
    val currentIsResting by rememberUpdatedState(isResting)
    val screenShape = rememberScreenShape()

    BoxWithConstraints(modifier.clipToBounds()) {
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        val area = Size(areaWidth, areaHeight)
        val padding = with(density) { framePadding.toRect(this, layoutDirection) }
        val bounds = remember(area, padding, density, state.screenAspectRatio) {
            FrameBounds.fit(area, padding, with(density) { PhoneFrameDefaults.BorderWidth.toPx() }, state.screenAspectRatio)
        }
        val turnedBounds = remember(bounds, area, padding) { bounds.turned(area, padding) }
        val turnScale = remember(bounds, area, padding) { bounds.turnScale(area, padding) }
        val screen = bounds.screen
        SideEffect {
            state.base.onFrameSize(screen.width, screen.height)
            state.turned.onFrameSize(turnedBounds.screen.width, turnedBounds.screen.height)
        }

        // Both frames have the same centre: the placements of both crops are from it.
        val frameCenter by rememberUpdatedState(screen.center)
        val gestures = Modifier.pointerInput(state) {
            // A gesture that starts while the frame turns is ignored to its end.
            var isFollowed = false
            detectCropGestures(
                onStart = {
                    isFollowed = currentIsResting
                    if (isFollowed) state.onGestureStart()
                },
                onGesture = { centroid, pan, zoom, rotation ->
                    if (isFollowed) {
                        val fromCenter = centroid - frameCenter
                        if (state.active.onGesture(fromCenter.x, fromCenter.y, pan.x, pan.y, zoom, rotation)) currentOnSnap()
                    }
                },
                onEnd = { if (isFollowed) state.active.onGestureEnd(scope) },
            )
        }

        // The picture is laid out once at the scale that just covers the base frame; gestures and turns only
        // change its layer.
        val media = state.base.mediaSize
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
                    .placedAt(pictureLeft.toFloat(), pictureTop.toFloat(), pictureWidth.toFloat(), pictureHeight.toFloat())
                    .graphicsLayer {
                        val placement = placementBetween(state.base.placement, state.turned.placement, turnProgress)
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
                val screenCorner = screenShape.screenCornerRadius(screen.size, this)
                val turn = turnOf(turnProgress, turnScale, screen.center)
                dimAround(screen, screenCorner, turn, background.copy(alpha = CropEditorDefaults.DIM_ALPHA))
                fadeAround(lerp(bounds.body, turnedBounds.body, turnProgress), background)
                val shown = if (state.isTurned) turnedBounds.screen else screen
                if (gridAlpha > 0f && isResting) {
                    drawThirds(shown, Color.White.copy(alpha = CropEditorDefaults.GRID_ALPHA * gridAlpha))
                }
                if (horizontalGuideAlpha > 0f) {
                    drawGuide(shown, horizontal = true, CropEditorDefaults.GuideColor.copy(alpha = horizontalGuideAlpha))
                }
                if (verticalGuideAlpha > 0f) {
                    drawGuide(shown, horizontal = false, CropEditorDefaults.GuideColor.copy(alpha = verticalGuideAlpha))
                }
            }

            // Always the phone as it stands now, turned and scaled as a whole when the screen is.
            PhoneFrame(
                modifier = Modifier
                    .placedAt(bounds.body.left, bounds.body.top, bounds.body.width, bounds.body.height)
                    .graphicsLayer {
                        rotationZ = TURN_DEGREES * turnProgress
                        val scale = lerp(1f, turnScale, turnProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                color = frameColor,
                screen = screenShape,
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

    /** How long the frame takes to turn, and the picture to go from one crop to the other. */
    const val TURN_MS = 400

    /**
     * How much smaller than it is laid out a [CropEditor] of [full] pixels is to be drawn, about its middle, to
     * show its whole frame inside [FramePadding] in only [visibleHeight] pixels of height: 1 while it fits as
     * it is. Drawn smaller as a whole, the frame keeps its proportions (border, buttons, cut-out) and the
     * picture under it its place, so the crop is the same to the pixel; laying it out anew for the smaller
     * room would keep those parts their size on a smaller body. The phone fits either way it is turned, so
     * that it doesn't jump as it turns.
     */
    fun scaleToFit(
        density: Density,
        layoutDirection: LayoutDirection,
        full: IntSize,
        visibleHeight: Int,
        screenAspectRatio: Float,
    ): Float = with(density) {
        FrameBounds.scaleToFitTurned(
            full = Size(full.width.toFloat(), full.height.toFloat()),
            visible = Size(full.width.toFloat(), visibleHeight.toFloat()),
            padding = FramePadding.toRect(this, layoutDirection),
            border = PhoneFrameDefaults.BorderWidth.toPx(),
            aspectRatio = screenAspectRatio,
        )
    }
}

/** The phone turns anticlockwise, as a phone turned to the left: its camera ends up on the left. */
private const val TURN_DEGREES = -90f

/** The phone's turn and scale at [progress] of the way, about [center]. */
private fun turnOf(progress: Float, turnScale: Float, center: Offset): Matrix = Matrix().apply {
    translate(center.x, center.y)
    rotateZ(TURN_DEGREES * progress)
    val scale = lerp(1f, turnScale, progress)
    scale(scale, scale)
    translate(-center.x, -center.y)
}

/**
 * Where the picture lies at [progress] of the way from the base crop's [from] to the turned crop's [to]: the
 * scale, the offset and the angle (the short way round) in between.
 */
private fun placementBetween(from: Placement?, to: Placement?, progress: Float): Placement? = when {
    progress <= 0f || to == null -> from
    progress >= 1f || from == null -> to
    else -> Placement(
        scale = lerp(from.scale, to.scale, progress),
        rotation = from.rotation + (((to.rotation - from.rotation) % 360f + 540f) % 360f - 180f) * progress,
        offsetX = lerp(from.offsetX, to.offsetX, progress),
        offsetY = lerp(from.offsetY, to.offsetY, progress),
    )
}

/** Covers everything but the [screen], as the phone's [turn] places it, with [color]. */
private fun DrawScope.dimAround(screen: Rect, cornerRadius: Float, turn: Matrix, color: Color) {
    val opening = Path().apply {
        addRoundRect(RoundRect(screen, CornerRadius(cornerRadius)))
        transform(turn)
    }
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
