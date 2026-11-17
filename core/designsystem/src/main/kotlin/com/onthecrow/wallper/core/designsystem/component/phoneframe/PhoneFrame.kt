package com.onthecrow.wallper.core.designsystem.component.phoneframe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A phone drawn in outline around a wallpaper: the body, two volume buttons on the left, the power button on
 * the right and the camera cut-out. It fills its bounds with the body; the buttons stand out past its sides.
 * The cut-out keeps its size whatever the body's proportions; the buttons follow the body's height. Its place
 * around a screen of given proportions is [FrameBounds]; [PhoneFramedScreen] puts content in it.
 *
 * The screen's corners are the device's own ([screen]), at the frame's scale; where Android doesn't say,
 * [PhoneFrameDefaults.ScreenCornerRadius].
 */
@Composable
fun PhoneFrame(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    screen: ScreenShape = ScreenShape.Unknown,
) {
    Layout(
        content = {
            PhoneFrameBody(color, screen)
            PhoneFrameButton(color)
            PhoneFrameButton(color)
            PhoneFrameButton(color)
            PhoneFrameNotch(color)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val border = PhoneFrameDefaults.BorderWidth.roundToPx()
        val thickness = PhoneFrameDefaults.ButtonThickness.roundToPx()
        // Each button reaches under the border, so only its outer side shows rounded.
        val buttonWidth = thickness + border / 2
        fun button(index: Int, length: Float) =
            measurables[index].measure(Constraints.fixed(buttonWidth, (height * length).roundToInt()))

        val body = measurables[0].measure(Constraints.fixed(width, height))
        val volumeUp = button(1, PhoneFrameDefaults.VOLUME_LENGTH)
        val volumeDown = button(2, PhoneFrameDefaults.VOLUME_LENGTH)
        val power = button(3, PhoneFrameDefaults.POWER_LENGTH)
        val notchSize = PhoneFrameDefaults.NotchSize
        val notch = measurables[4].measure(Constraints.fixed(notchSize.width.roundToPx(), notchSize.height.roundToPx()))

        layout(width, height) {
            body.place(0, 0)
            volumeUp.place(-thickness, (height * PhoneFrameDefaults.VOLUME_UP_TOP).roundToInt())
            volumeDown.place(-thickness, (height * PhoneFrameDefaults.VOLUME_DOWN_TOP).roundToInt())
            power.place(width + thickness - buttonWidth, (height * PhoneFrameDefaults.POWER_TOP).roundToInt())
            notch.place((width - notch.width) / 2, border + PhoneFrameDefaults.NotchTop.roundToPx())
        }
    }
}

/** The body: a see-through rounded rectangle with a thick outline, its inner corners the screen's. */
@Composable
private fun PhoneFrameBody(color: Color, screen: ScreenShape, modifier: Modifier = Modifier) {
    Box(modifier.border(PhoneFrameDefaults.BorderWidth, color, BodyShape(screen)))
}

/** A side button: a rounded bar. */
@Composable
private fun PhoneFrameButton(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color, RoundedCornerShape(percent = 50)))
}

/** The camera cut-out: a pill at the top of the screen. */
@Composable
private fun PhoneFrameNotch(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(color, RoundedCornerShape(percent = 50)))
}

/** The body's outline: its outer corners round the screen's by the border's width. */
private class BodyShape(private val screen: ScreenShape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val border = with(density) { PhoneFrameDefaults.BorderWidth.toPx() }
        val radius = border + screen.screenCornerRadius(Size(size.width - 2 * border, size.height - 2 * border), density)
        return Outline.Generic(Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius))) })
    }
}

/** The screen's corners in a frame whose screen is [screen] pixels: the device's, or the frame's own. */
fun ScreenShape.screenCornerRadius(screen: Size, density: Density): Float =
    cornerRadiusOn(screen, fallback = with(density) { PhoneFrameDefaults.ScreenCornerRadius.toPx() })

object PhoneFrameDefaults {
    val BorderWidth = 8.dp
    val CornerRadius = 32.dp

    /** The screen's own corners, inside the border, where the device's are unknown. */
    val ScreenCornerRadius = CornerRadius - BorderWidth

    val ButtonThickness = 4.dp
    val NotchSize = DpSize(72.dp, 20.dp)

    /** From the border to the cut-out. */
    val NotchTop = 8.dp

    // Where the buttons start and how long they are, in heights of the body.
    const val VOLUME_UP_TOP = 0.158f
    const val VOLUME_DOWN_TOP = 0.259f
    const val VOLUME_LENGTH = 0.084f
    const val POWER_TOP = 0.158f
    const val POWER_LENGTH = 0.125f
}

@Preview(widthDp = 300, heightDp = 560)
@Composable
private fun PhoneFramePreview() {
    Box(Modifier.background(Color(0xFF14101C))) {
        PhoneFrame(
            Modifier
                .padding(start = 24.dp, top = 16.dp)
                .size(246.dp, 528.dp)
        )
    }
}
