package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.CropRect

/**
 * Texture coordinates of the crop corners in the upright frame, origin at the bottom left, as
 * SurfaceTexture's transform matrix expects them. Rotation is that matrix's job, not ours.
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
            val left = rect.left.toFloat() / frameWidth
            val right = rect.right.toFloat() / frameWidth
            // Texture coordinates grow upwards, the crop rect downwards.
            val top = 1f - rect.top.toFloat() / frameHeight
            val bottom = 1f - rect.bottom.toFloat() / frameHeight
            return UVCoords(
                topLeft = UVPoint(left, top),
                topRight = UVPoint(right, top),
                bottomLeft = UVPoint(left, bottom),
                bottomRight = UVPoint(right, bottom),
            )
        }
    }
}

internal data class UVPoint(val u: Float, val v: Float)
