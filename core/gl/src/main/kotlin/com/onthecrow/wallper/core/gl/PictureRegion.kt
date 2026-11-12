package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * The part of a picture its wallpaper can ever show, in pixels of the upright [frameWidth] x [frameHeight]
 * frame: the crop window, turned with the crop, wherever [PageScroll] takes it. Only this part is decoded.
 */
internal class PictureRegion(
    private val frameWidth: Int,
    private val frameHeight: Int,
    crop: CropRect,
) {
    val left: Int
    val top: Int
    val right: Int
    val bottom: Int

    val width: Int get() = right - left
    val height: Int get() = bottom - top

    init {
        val corners = CropCorners(crop).all
        val scroll = PageScroll(frameWidth, frameHeight, crop)
        // The window moves along a straight line, so its ends and the crop as chosen bound everything between.
        val shifts = listOf(UVPoint(0f, 0f), scroll.shift(0f), scroll.shift(1f))
        val xs = shifts.flatMap { shift -> corners.map { it.x + shift.u * frameWidth } }
        val ys = shifts.flatMap { shift -> corners.map { it.y - shift.v * frameHeight } }
        left = (floor(xs.min()).toInt() - MARGIN).coerceIn(0, frameWidth)
        top = (floor(ys.min()).toInt() - MARGIN).coerceIn(0, frameHeight)
        right = (ceil(xs.max()).toInt() + MARGIN).coerceIn(left, frameWidth)
        bottom = (ceil(ys.max()).toInt() + MARGIN).coerceIn(top, frameHeight)
    }

    /**
     * How far to subsample the region, a power of two: as far as the crop still has a pixel for each of the
     * screen's, and then as far as the region fits in a texture of [maxTextureSize].
     */
    fun sampleSize(crop: CropRect, screenWidth: Int, screenHeight: Int, maxTextureSize: Int): Int {
        val spare = min(crop.width.toFloat() / screenWidth.coerceAtLeast(1), crop.height.toFloat() / screenHeight.coerceAtLeast(1))
        var sample = Integer.highestOneBit(spare.toInt().coerceAtLeast(1))
        while (ceilDiv(width, sample) > maxTextureSize || ceilDiv(height, sample) > maxTextureSize) sample *= 2
        return sample
    }

    /**
     * Maps the frame's texture coordinates (0 to 1, v up, as [UVCoords] and [PageScroll] give them) onto the
     * region's texture, a bitmap uploaded top row first. Column-major, for `glUniformMatrix4fv`.
     */
    fun textureMatrix(): FloatArray = FloatArray(16).apply {
        val regionWidth = width.coerceAtLeast(1).toFloat()
        val regionHeight = height.coerceAtLeast(1).toFloat()
        // s = (u * frameWidth - left) / regionWidth; t = ((1 - v) * frameHeight - top) / regionHeight.
        this[0] = frameWidth / regionWidth
        this[5] = -frameHeight / regionHeight
        this[10] = 1f
        this[12] = -left / regionWidth
        this[13] = (frameHeight - top) / regionHeight
        this[15] = 1f
    }

    private fun ceilDiv(value: Int, divisor: Int) = (value + divisor - 1) / divisor

    private companion object {
        /** A few pixels around the window, so that filtering at its edges reads real picture. */
        const val MARGIN = 2
    }
}
