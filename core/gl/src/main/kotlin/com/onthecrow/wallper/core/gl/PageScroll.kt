package com.onthecrow.wallper.core.gl

import kotlin.math.min

/**
 * Pans a video's crop window with the launcher's pages, like a static wallpaper wider than the screen.
 * The window travels [TRAVEL] of its width from the first page to the last, as far as the frame allows,
 * and shows the crop as chosen on the middle page when it can: a crop at an edge of the frame shows on
 * the page nearest to that edge instead.
 */
internal object PageScroll {

    /** How far the window travels across all the pages, in widths of the crop. */
    const val TRAVEL = 0.5f

    /**
     * The window's horizontal shift, in texture coordinates of the upright frame (0 to 1), for a crop from
     * [cropLeft] to [cropRight] in the same coordinates, at the launcher's [pageOffset] (0 to 1).
     */
    fun shiftU(cropLeft: Float, cropRight: Float, pageOffset: Float): Float {
        val width = cropRight - cropLeft
        val travel = min(width * TRAVEL, 1f - width).coerceAtLeast(0f)
        val lastStart = (1f - width - travel).coerceAtLeast(0f)
        val firstPageLeft = (cropLeft - travel / 2).coerceIn(0f, lastStart)
        return firstPageLeft + pageOffset.coerceIn(0f, 1f) * travel - cropLeft
    }
}
