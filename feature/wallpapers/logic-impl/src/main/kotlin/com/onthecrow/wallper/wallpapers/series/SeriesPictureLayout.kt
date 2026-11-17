package com.onthecrow.wallper.wallpapers.series

import kotlin.math.tan

/**
 * How [count] pictures share a series' picture of [width] × [height]: side by side, each its share of the width,
 * parted by lines that lean right going up, [LEAN_DEGREES] off the upright. Each line crosses the middle of the
 * height where the shares meet, so the first part narrows towards the bottom and the last towards the top.
 */
internal class SeriesPictureLayout(val count: Int, val width: Float, val height: Float) {

    private val lean = tan(Math.toRadians(LEAN_DEGREES)).toFloat()

    /** Where the line between part [index] - 1 and part [index] crosses height [y], y down. */
    fun lineX(index: Int, y: Float): Float = width * index / count + lean * (height / 2 - y)

    /**
     * The left of the box part [index]'s picture fills: where its left line is furthest left, inside the
     * picture. The box spans the whole height, so it holds the whole part.
     */
    fun boxLeft(index: Int): Float = if (index == 0) 0f else lineX(index, height).coerceAtLeast(0f)

    /** The right of the box part [index]'s picture fills: where its right line is furthest right. */
    fun boxRight(index: Int): Float = if (index == count - 1) width else lineX(index + 1, 0f).coerceAtMost(width)

    companion object {
        const val LEAN_DEGREES = 20.0
    }
}
