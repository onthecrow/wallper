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

    /**
     * The same phone turned a quarter about its centre and scaled as a whole, border and all, to be as large as
     * fits [area] inside [padding]: its body and screen as they then lie, with swapped proportions.
     */
    fun turned(area: Size, padding: Rect): FrameBounds {
        val scale = turnScale(area, padding)
        val center = body.center
        fun turnedRect(rect: Rect) = Rect(
            offset = Offset(center.x - rect.height * scale / 2, center.y - rect.width * scale / 2),
            size = Size(rect.height * scale, rect.width * scale),
        )
        return FrameBounds(body = turnedRect(body), screen = turnedRect(screen))
    }

    /** How much larger (or smaller) the phone is drawn [turned]. */
    fun turnScale(area: Size, padding: Rect): Float {
        if (body.width <= 0f || body.height <= 0f) return 1f
        val zoneWidth = area.width - padding.left - padding.right
        val zoneHeight = area.height - padding.top - padding.bottom
        return min(zoneWidth / body.height, zoneHeight / body.width).coerceAtLeast(0f)
    }

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
        fun scaleToFit(full: Size, visible: Size, padding: Rect, border: Float, aspectRatio: Float): Float =
            scaleToFit(fit(full, padding, border, aspectRatio).body, visible, padding)

        /** The same for a phone also shown [turned]: the scale at which either way fits, so that it stays as it turns. */
        fun scaleToFitTurned(full: Size, visible: Size, padding: Rect, border: Float, aspectRatio: Float): Float {
            val bounds = fit(full, padding, border, aspectRatio)
            return min(scaleToFit(bounds.body, visible, padding), scaleToFit(bounds.turned(full, padding).body, visible, padding))
        }

        private fun scaleToFit(body: Rect, visible: Size, padding: Rect): Float {
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
