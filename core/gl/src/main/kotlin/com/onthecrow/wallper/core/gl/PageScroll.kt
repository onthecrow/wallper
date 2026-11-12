package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pans a video's crop window with the launcher's pages, like a static wallpaper wider than the screen.
 * The window travels [TRAVEL] of its width from the first page to the last along the screen's horizontal
 * (which the crop's rotation turns in the frame), as far as the frame allows, and shows the crop as chosen
 * on the middle page when it can: a crop at an edge of the frame shows on the page nearest to that edge
 * instead.
 *
 * [crop] is in pixels of the upright [frameWidth] x [frameHeight] frame.
 */
internal class PageScroll(private val frameWidth: Int, private val frameHeight: Int, crop: CropRect) {

    private val axis: PixelPoint

    /** The window's distance from the crop on the first page, in pixels along [axis]. */
    private val firstPage: Float

    /** How far the window goes from the first page to the last, in pixels along [axis]. */
    private val travel: Float

    init {
        val corners = CropCorners(crop)
        axis = corners.axisX
        // How far the window can go back (negative) and forth along the axis with every corner in the frame.
        var back = -Float.MAX_VALUE
        var forth = Float.MAX_VALUE
        fun limit(position: Float, direction: Float, size: Int) {
            if (abs(direction) < EPSILON) return
            val toStart = -position / direction
            val toEnd = (size - position) / direction
            back = max(back, min(toStart, toEnd))
            forth = min(forth, max(toStart, toEnd))
        }
        corners.all.forEach { corner ->
            limit(corner.x, axis.x, frameWidth)
            limit(corner.y, axis.y, frameHeight)
        }
        // A crop a rounding error past an edge stays where it is.
        back = back.coerceAtMost(0f)
        forth = forth.coerceAtLeast(0f)

        travel = min(crop.width * TRAVEL, forth - back).coerceAtLeast(0f)
        firstPage = (-travel / 2).coerceIn(back, forth - travel)
    }

    /**
     * The window's shift at the launcher's [pageOffset] (0 to 1), in texture coordinates of the upright
     * frame (0 to 1, v pointing up).
     */
    fun shift(pageOffset: Float): UVPoint {
        val distance = firstPage + pageOffset.coerceIn(0f, 1f) * travel
        return UVPoint(u = distance * axis.x / frameWidth, v = -distance * axis.y / frameHeight)
    }

    companion object {
        /** How far the window travels across all the pages, in widths of the crop. */
        const val TRAVEL = 0.5f

        private const val EPSILON = 1e-6f
    }
}
