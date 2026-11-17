package com.onthecrow.wallper.wallpapers.group

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * A rounded rectangle with [cradle] cut into the middle of its top edge, like the one Material's bottom app bar
 * cuts for its FAB, in pixels. Without a cradle, or with one that cuts nothing or doesn't fit between the
 * corners, it is [cornerRadius]'s rounded rectangle.
 */
internal data class CradleShape(val cornerRadius: Float, val cradle: CradleGeometry?) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = cornerRadius.coerceAtMost(size.minDimension / 2)
        val middle = size.width / 2
        if (cradle == null || !cradle.cuts || cradle.halfWidth > middle - corner) {
            return Outline.Rounded(RoundRect(size.toRect(), CornerRadius(corner)))
        }
        val a = cradle.filletAngle
        val path = Path().apply {
            moveTo(0f, corner)
            arcTo(Rect(0f, 0f, 2 * corner, 2 * corner), 180f, 90f, false)
            // Along the edge to the left fillet, down it onto the left end, round that to the bottom, across
            // (arcTo draws the line), round the right end and up the right fillet.
            arcTo(Rect(Offset(middle - cradle.halfWidth, cradle.fillet), cradle.fillet), -90f, a - 90f, false)
            arcTo(Rect(Offset(middle - cradle.halfStraight, -cradle.lift), cradle.radius), a, 90f - a, false)
            arcTo(Rect(Offset(middle + cradle.halfStraight, -cradle.lift), cradle.radius), 90f, 90f - a, false)
            arcTo(Rect(Offset(middle + cradle.halfWidth, cradle.fillet), cradle.fillet), -a, a - 90f, false)
            lineTo(size.width - corner, 0f)
            arcTo(Rect(size.width - 2 * corner, 0f, size.width, 2 * corner), 270f, 90f, false)
            lineTo(size.width, size.height - corner)
            arcTo(Rect(size.width - 2 * corner, size.height - 2 * corner, size.width, size.height), 0f, 90f, false)
            lineTo(corner, size.height)
            arcTo(Rect(0f, size.height - 2 * corner, 2 * corner, size.height), 90f, 90f, false)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * A cradle about the middle of an edge for a button with round ends, a pill or a circle: the edge at y 0, y
 * down; angles in degrees, clockwise from +x, as `Path.arcTo` takes them.
 *
 * Its ends are circles of [radius] whose centres are [halfStraight] either side of the middle (0 for a round
 * button) and [lift] above the edge, joined at the bottom by a straight line. Each fillet is a circle of
 * [fillet] that touches the edge from below and an end from outside, so the outline turns from one to the next
 * without a corner. The higher the lift, the shallower and narrower the cradle, down to nothing at [radius].
 */
internal data class CradleGeometry(val radius: Float, val halfStraight: Float, val lift: Float, val fillet: Float) {

    /** Whether the cradle reaches below the edge. */
    val cuts: Boolean get() = radius > lift

    /** Across from an end's centre to where its fillet touches the edge. */
    private val filletReach: Float =
        sqrt(((radius + fillet) * (radius + fillet) - (fillet + lift) * (fillet + lift)).coerceAtLeast(0f))

    /** From the middle to where each fillet touches the edge: the cut ends there. */
    val halfWidth: Float get() = halfStraight + filletReach

    /** Where the left fillet's centre is seen from the left end's centre, between 90° and 180°: down and left. */
    val filletAngle: Float = Math.toDegrees(atan2((fillet + lift).toDouble(), -filletReach.toDouble())).toFloat()
}
