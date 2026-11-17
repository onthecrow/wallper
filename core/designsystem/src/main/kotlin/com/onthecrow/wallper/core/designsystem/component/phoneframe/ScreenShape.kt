package com.onthecrow.wallper.core.designsystem.component.phoneframe

import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import kotlin.math.min

/**
 * The device's screen, for a [PhoneFrame] to look like it: its corners' radius, in shorter sides of the
 * screen; null where Android doesn't say (before 12, or a screen with square corners): the frame has its own.
 *
 * The camera's cut-out isn't the device's: Android often knows only a box for it (a OnePlus outlines its hole
 * as a rectangle from the screen's top edge), and drawn so it was a notch the phone hasn't.
 */
@Immutable
data class ScreenShape(val cornerRadius: Float? = null) {

    /** The corners' radius on a frame's screen of [screen] pixels; [fallback] where unknown. */
    fun cornerRadiusOn(screen: Size, fallback: Float): Float =
        cornerRadius?.let { it * min(screen.width, screen.height) } ?: fallback

    companion object {
        /** Nothing known: the frame's own corners. */
        val Unknown = ScreenShape()
    }
}

/** This device's screen; read again when the window changes size. */
@Composable
fun rememberScreenShape(): ScreenShape {
    val context = LocalContext.current
    val windowSize = LocalWindowInfo.current.containerSize
    return remember(context, windowSize) { screenShapeOf(context) }
}

private fun screenShapeOf(context: Context): ScreenShape {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ScreenShape.Unknown
    val metrics = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics ?: return ScreenShape.Unknown
    val bounds = metrics.bounds
    val shorter = min(bounds.width(), bounds.height()).toFloat()
    if (shorter <= 0f) return ScreenShape.Unknown
    val radius = metrics.windowInsets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.takeIf { it > 0 }
    return ScreenShape(cornerRadius = radius?.let { it / shorter })
}
