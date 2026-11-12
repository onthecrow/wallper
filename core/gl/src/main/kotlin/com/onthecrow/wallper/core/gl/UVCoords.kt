package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Texture coordinates of the crop corners in the upright frame, origin at the bottom left, as
 * SurfaceTexture's transform matrix expects them. The video's own rotation is that matrix's job, not ours;
 * the crop's [CropRect.rotation] turns the corners here.
 */
internal class UVCoords(
    val topLeft: UVPoint,
    val topRight: UVPoint,
    val bottomLeft: UVPoint,
    val bottomRight: UVPoint,
) {
    companion object {
        /** [rect] is in pixels of the frame as shown, [frameWidth] x [frameHeight] after rotation. */
        fun fromRect(frameWidth: Int, frameHeight: Int, rect: CropRect): UVCoords {
            val corners = CropCorners(rect)
            // Texture coordinates grow upwards, the crop rect downwards.
            fun PixelPoint.toUv() = UVPoint(x / frameWidth, 1f - y / frameHeight)
            return UVCoords(
                topLeft = corners.topLeft.toUv(),
                topRight = corners.topRight.toUv(),
                bottomLeft = corners.bottomLeft.toUv(),
                bottomRight = corners.bottomRight.toUv(),
            )
        }
    }
}

internal data class UVPoint(val u: Float, val v: Float)

/** A point in pixels of the upright frame, y pointing down. */
internal data class PixelPoint(val x: Float, val y: Float)

/**
 * The corners of [rect] in pixels of the frame, turned by its rotation: the screen's top left corner shows
 * [topLeft], and so on. [axisX] is the screen's x axis in the frame, a unit vector.
 */
internal class CropCorners(rect: CropRect) {
    private val radians = Math.toRadians(rect.rotation.toDouble())
    private val cos = cos(radians).toFloat()
    private val sin = sin(radians).toFloat()
    private val halfWidth = rect.width / 2f
    private val halfHeight = rect.height / 2f
    private val centerX = rect.centerX
    private val centerY = rect.centerY

    val axisX = PixelPoint(cos, -sin)
    val topLeft = corner(-halfWidth, -halfHeight)
    val topRight = corner(halfWidth, -halfHeight)
    val bottomLeft = corner(-halfWidth, halfHeight)
    val bottomRight = corner(halfWidth, halfHeight)
    val all = listOf(topLeft, topRight, bottomLeft, bottomRight)

    /** The point [dx], [dy] from the centre on the screen, turned back by -rotation into the frame. */
    private fun corner(dx: Float, dy: Float) = PixelPoint(
        x = centerX + dx * cos + dy * sin,
        y = centerY - dx * sin + dy * cos,
    )
}
