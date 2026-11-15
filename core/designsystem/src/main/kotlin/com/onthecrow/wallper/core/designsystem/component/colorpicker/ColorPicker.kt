package com.onthecrow.wallper.core.designsystem.component.colorpicker

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil

/**
 * Picks a colour and how opaque it is, on one rounded card: an area of saturation (white on the left, the
 * pure hue on the right) and value (black at the bottom), the bar of hues under it, and under that the bar of
 * alpha, from transparent over a chequerboard to the colour itself. Each has a round thumb; a touch anywhere
 * on a part puts its thumb there, and a drag stays with the part it started on.
 *
 * It keeps nothing: [color] is what it shows, [onColorChange] what a touch asks for. The card is inset from
 * the picker's bounds by as much as a thumb at an edge reaches out ([ColorPickerDefaults.EdgeInset] on either
 * side). The two bars are kept from the system's edge gestures, so a thumb at the screen's edge can be
 * dragged; the area above them is not, so «back» still works over most of the picker. Its geometry lives in
 * `ColorPickerMath.kt` and is tested on the JVM.
 */
@Composable
fun ColorPicker(
    color: HsvColor,
    onColorChange: (HsvColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Read while drawing and by the gesture, so neither is set up again for each colour.
    val currentColor by rememberUpdatedState(color)
    val currentOnColorChange by rememberUpdatedState(onColorChange)
    val density = LocalDensity.current
    Spacer(
        modifier
            .fillMaxWidth()
            .height(ColorPickerDefaults.Height)
            // A thumb at either end of a bar is where the system takes a swipe for «back»: there the drag is
            // the picker's. Only there: the bars, and the area's bottom edge as far as its thumb reaches. A
            // swipe from the screen's edge anywhere higher is still «back».
            .systemGestureExclusion { coordinates ->
                val size = coordinates.size.toSize()
                val top = with(density) { geometryOf(size).hueTop - ColorPickerDefaults.AreaThumbRadius.toPx() }
                Rect(left = 0f, top = top.coerceAtLeast(0f), right = size.width, bottom = size.height)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    // No touch slop: the thumb goes under the finger as it comes down.
                    val down = awaitFirstDown()
                    val geometry = geometryOf(size.toSize())
                    val part = geometry.partAt(down.position.y)
                    down.consume()
                    currentOnColorChange(geometry.colorAt(part, down.position.x, down.position.y, currentColor))
                    drag(down.id) { change ->
                        change.consume()
                        currentOnColorChange(geometry.colorAt(part, change.position.x, change.position.y, currentColor))
                    }
                }
            }
            .drawWithCache {
                // What no colour changes is made once for a size.
                val geometry = geometryOf(size)
                val area = Rect(geometry.left, geometry.top, geometry.right, geometry.hueTop)
                val hueBar = Rect(geometry.left, geometry.hueTop, geometry.right, geometry.alphaTop)
                val alphaBar = Rect(geometry.left, geometry.alphaTop, geometry.right, geometry.alphaBottom)
                val card = Path().apply {
                    val bounds = Rect(geometry.left, geometry.top, geometry.right, geometry.alphaBottom)
                    addRoundRect(RoundRect(bounds, CornerRadius(ColorPickerDefaults.CornerRadius.toPx())))
                }
                val toBlack = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = area.top,
                    endY = area.bottom,
                )
                val hues = Brush.horizontalGradient(HUES, startX = hueBar.left, endX = hueBar.right)
                val chequers = chequersOf(alphaBar, ColorPickerDefaults.ChequerSize.toPx())
                val areaThumbRadius = ColorPickerDefaults.AreaThumbRadius.toPx()
                val barThumbRadius = ColorPickerDefaults.BarThumbRadius.toPx()
                val thumbBorder = ColorPickerDefaults.ThumbBorder.toPx()
                val thumbShadow = ColorPickerDefaults.ThumbShadow.toPx()

                onDrawBehind {
                    val picked = currentColor
                    val hue = Color(HsvColor(picked.hue, saturation = 1f, value = 1f).toArgb())
                    val opaque = Color(picked.copy(alpha = 1f).toArgb())
                    clipPath(card) {
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(Color.White, hue), startX = area.left, endX = area.right),
                            topLeft = area.topLeft,
                            size = area.size,
                        )
                        drawRect(toBlack, area.topLeft, area.size)
                        drawRect(hues, hueBar.topLeft, hueBar.size)
                        drawRect(Color.White, alphaBar.topLeft, alphaBar.size)
                        drawPath(chequers, CHEQUER_COLOR)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(opaque.copy(alpha = 0f), opaque),
                                startX = alphaBar.left,
                                endX = alphaBar.right,
                            ),
                            topLeft = alphaBar.topLeft,
                            size = alphaBar.size,
                        )
                    }
                    // Over all three parts: a thumb at the edge of one reaches onto the next.
                    drawThumb(
                        center = Offset(geometry.saturationX(picked), geometry.valueY(picked)),
                        radius = areaThumbRadius,
                        border = thumbBorder,
                        shadow = thumbShadow,
                        fill = opaque,
                    )
                    drawThumb(Offset(geometry.hueX(picked), hueBar.center.y), barThumbRadius, thumbBorder, thumbShadow, hue)
                    drawThumb(
                        center = Offset(geometry.alphaX(picked), alphaBar.center.y),
                        radius = barThumbRadius,
                        border = thumbBorder,
                        shadow = thumbShadow,
                        // Over the thumb's white, as on the bar.
                        fill = opaque.copy(alpha = picked.alpha.coerceIn(0f, 1f)),
                    )
                }
            }
    )
}

object ColorPickerDefaults {
    /** The saturation and value area. */
    val AreaHeight = 160.dp

    /** The hue bar, and the alpha bar under it. */
    val BarHeight = 32.dp
    val CornerRadius = 12.dp
    internal val AreaThumbRadius = 14.dp
    internal val BarThumbRadius = 18.dp
    internal val ThumbBorder = 3.dp
    internal val ThumbShadow = 1.dp
    internal val ChequerSize = 8.dp

    /** How far the card is from the picker's left and right edges: what a bar's thumb at its end reaches out. */
    val EdgeInset: Dp = BarThumbRadius

    /** The picker's height: the three parts and the thumbs that reach out above and below them. */
    val Height: Dp = AreaThumbRadius + AreaHeight + BarHeight * 2 + (BarThumbRadius - BarHeight / 2)
}

private fun Density.geometryOf(size: Size) = ColorPickerGeometry(
    width = size.width,
    height = size.height,
    barHeight = ColorPickerDefaults.BarHeight.toPx(),
    insetX = ColorPickerDefaults.EdgeInset.toPx(),
    insetTop = ColorPickerDefaults.AreaThumbRadius.toPx(),
    insetBottom = (ColorPickerDefaults.BarThumbRadius - ColorPickerDefaults.BarHeight / 2).toPx(),
)

/** A white ring around [fill], with a soft dark rim that parts it from a light colour under it. */
private fun DrawScope.drawThumb(center: Offset, radius: Float, border: Float, shadow: Float, fill: Color) {
    drawCircle(THUMB_SHADOW_COLOR, radius + shadow, center)
    drawCircle(Color.White, radius, center)
    drawCircle(fill, radius - border, center)
}

/** Every other square of [bar], [cell] wide, from its top left corner: the grey ones of a chequerboard. */
private fun chequersOf(bar: Rect, cell: Float): Path = Path().apply {
    if (cell <= 0f) return@apply
    val columns = ceil(bar.width / cell).toInt()
    val rows = ceil(bar.height / cell).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column) % 2 == 0) continue
            val left = bar.left + column * cell
            val top = bar.top + row * cell
            addRect(Rect(left, top, minOf(left + cell, bar.right), minOf(top + cell, bar.bottom)))
        }
    }
}

/** Red, through yellow, green, cyan, blue and magenta, back to red: [HsvColor.hue] from 0 to its maximum. */
private val HUES = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
private val CHEQUER_COLOR = Color(0xFFD9D9D9)
private val THUMB_SHADOW_COLOR = Color(0x33000000)

@Preview
@Composable
private fun ColorPickerPreview() {
    MaterialTheme {
        Surface {
            var color by remember { mutableStateOf(HsvColor(hue = 110f, saturation = 0.35f, value = 0.75f, alpha = 0.9f)) }
            ColorPicker(color = color, onColorChange = { color = it }, modifier = Modifier.padding(8.dp))
        }
    }
}
