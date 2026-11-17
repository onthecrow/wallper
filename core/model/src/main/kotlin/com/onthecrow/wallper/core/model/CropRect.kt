package com.onthecrow.wallper.core.model

/**
 * A crop area in pixels of the frame as shown, after the video's rotation
 * ([MediaInfo.displayWidth] x [MediaInfo.displayHeight]). Plain Kotlin replacement for `android.graphics.Rect`.
 *
 * [rotation] turns the picture clockwise on the screen, in degrees: the area is [left]..[right] x
 * [top]..[bottom] turned about its centre the other way, by -[rotation]. With a rotation the rect itself may
 * reach past the frame's edges; the turned area stays inside.
 */
data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val rotation: Float = 0f,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}
