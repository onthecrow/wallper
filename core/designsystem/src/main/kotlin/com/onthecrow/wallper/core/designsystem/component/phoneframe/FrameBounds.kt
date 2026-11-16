package com.onthecrow.wallper.core.designsystem.component.phoneframe

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/** The phone's [body] (its outer edge) and its [screen], the opening inside it, in pixels of the area. */
data class FrameBounds(val body: Rect, val screen: Rect) {
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

        /**
         * The scale at which the body [fit] into [full] also fits [visible] inside the same [padding]: 1 if it
         * does already, never more, and 0 when [visible] leaves it no room at all.
         */
        fun scaleToFit(full: Size, visible: Size, padding: Rect, border: Float, aspectRatio: Float): Float {
            val body = fit(full, padding, border, aspectRatio).body
            if (body.width <= 0f || body.height <= 0f) return 1f
            val roomWidth = visible.width - padding.left - padding.right
            val roomHeight = visible.height - padding.top - padding.bottom
            return minOf(1f, roomWidth / body.width, roomHeight / body.height).coerceAtLeast(0f)
        }
    }
}

/** Each side's inset in pixels. */
fun PaddingValues.toRect(density: Density, layoutDirection: LayoutDirection): Rect = with(density) {
    Rect(
        left = calculateStartPadding(layoutDirection).toPx(),
        top = calculateTopPadding().toPx(),
        right = calculateEndPadding(layoutDirection).toPx(),
        bottom = calculateBottomPadding().toPx(),
    )
}
