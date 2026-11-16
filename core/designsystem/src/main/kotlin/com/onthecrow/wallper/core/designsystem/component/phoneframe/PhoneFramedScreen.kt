package com.onthecrow.wallper.core.designsystem.component.phoneframe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.window.layout.WindowMetricsCalculator
import kotlin.math.roundToInt

/**
 * [screen] in a [PhoneFrame] whose screen has [aspectRatio] (width / height), as large as fits inside
 * [padding], centred: laid out at the screen's size and clipped to its rounded corners, so nothing of it shows
 * outside the frame.
 */
@Composable
fun PhoneFramedScreen(
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    screen: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    BoxWithConstraints(modifier) {
        val area = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val bounds = remember(area, padding, density, layoutDirection, aspectRatio) {
            FrameBounds.fit(
                area = area,
                padding = padding.toRect(density, layoutDirection),
                border = with(density) { PhoneFrameDefaults.BorderWidth.toPx() },
                aspectRatio = aspectRatio,
            )
        }
        Box(
            Modifier
                .placedAt(bounds.screen.left, bounds.screen.top, bounds.screen.width, bounds.screen.height)
                .clip(RoundedCornerShape(PhoneFrameDefaults.ScreenCornerRadius))
        ) {
            screen()
        }
        PhoneFrame(
            modifier = Modifier.placedAt(bounds.body.left, bounds.body.top, bounds.body.width, bounds.body.height),
            color = color,
        )
    }
}

/** Lays the content out at [width] x [height] pixels, placed at [left], [top] of a parent it fills. */
fun Modifier.placedAt(left: Float, top: Float, width: Float, height: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints.fixed(width.roundToInt().coerceAtLeast(0), height.roundToInt().coerceAtLeast(0))
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(left.roundToInt(), top.roundToInt()) }
}

/** The proportions (width / height) of the screen the wallpaper is drawn on, whatever the window's. */
@Composable
fun rememberScreenAspectRatio(): Float {
    val context = LocalContext.current
    return remember(context) {
        val bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(context).bounds
        bounds.width().toFloat() / bounds.height().toFloat()
    }
}
