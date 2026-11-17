package com.onthecrow.wallper.core.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How a wallpaper is cropped for each way its screen can be turned: [crop] for the orientation it was made in
 * (its own proportions say which), [turned] for the other one, or null to derive that from [crop]
 * ([CropRect.reshaped]). Both are in pixels of the same upright frame.
 *
 * Whether a launcher turns, and whether it will, can't be known: every screen gets a window, never a stretched
 * crop.
 */
data class Framing(val crop: CropRect, val turned: CropRect? = null) {

    /**
     * The window to show on a screen of [screenWidth] x [screenHeight] over a frame of [frameWidth] x
     * [frameHeight]: of [crop] and [turned], the one nearer the screen's proportions, reshaped to them.
     */
    fun forScreen(screenWidth: Int, screenHeight: Int, frameWidth: Int, frameHeight: Int): CropRect {
        if (screenWidth <= 0 || screenHeight <= 0) return crop
        val aspect = screenWidth.toFloat() / screenHeight
        val nearest = turned?.takeIf { it.aspectDistance(aspect) < crop.aspectDistance(aspect) } ?: crop
        return nearest.reshaped(aspect, frameWidth, frameHeight)
    }
}

/**
 * This crop in the proportions [aspect] (width / height): the same centre, rotation and area, slid inside the
 * [frameWidth] x [frameHeight] frame, and made smaller, its proportions kept, only where it can't fit at all.
 * A crop already in those proportions (to a hundredth) is given back as it is. Android derives a static
 * wallpaper's crop for a turned screen the same way (`WallpaperCropper.getCrop`).
 */
fun CropRect.reshaped(aspect: Float, frameWidth: Int, frameHeight: Int): CropRect {
    if (width <= 0 || height <= 0 || aspect <= 0f || frameWidth <= 0 || frameHeight <= 0) return this
    if (aspectDistance(aspect) <= ASPECT_TOLERANCE) return this

    val area = width.toDouble() * height
    var halfWidth = sqrt(area * aspect) / 2
    var halfHeight = sqrt(area / aspect) / 2
    // The window is turned by its rotation in the frame: these are its half extents along the frame's axes.
    val radians = Math.toRadians(rotation.toDouble())
    val cos = abs(cos(radians))
    val sin = abs(sin(radians))
    fun extentX() = halfWidth * cos + halfHeight * sin
    fun extentY() = halfWidth * sin + halfHeight * cos

    val fit = minOf(1.0, frameWidth / (2 * extentX()), frameHeight / (2 * extentY()))
    halfWidth *= fit
    halfHeight *= fit
    val centerX = centerX.toDouble().slidIn(extentX(), frameWidth)
    val centerY = centerY.toDouble().slidIn(extentY(), frameHeight)
    return CropRect(
        left = (centerX - halfWidth).roundToInt(),
        top = (centerY - halfHeight).roundToInt(),
        right = (centerX + halfWidth).roundToInt(),
        bottom = (centerY + halfHeight).roundToInt(),
        rotation = rotation,
    )
}

/** How far this crop's proportions are from [aspect], as the logarithm of their ratio: 0 for the same. */
private fun CropRect.aspectDistance(aspect: Float): Double =
    if (width <= 0 || height <= 0) Double.MAX_VALUE else abs(ln(width.toDouble() / height / aspect))

/** This centre moved as little as needed for [extent] on both sides of it to stay within 0..[size]. */
private fun Double.slidIn(extent: Double, size: Int): Double =
    if (2 * extent >= size) size / 2.0 else coerceIn(extent, size - extent)

/** Proportions this near (as the logarithm of their ratio) are the same: the crop stays as it was saved. */
private const val ASPECT_TOLERANCE = 0.01
