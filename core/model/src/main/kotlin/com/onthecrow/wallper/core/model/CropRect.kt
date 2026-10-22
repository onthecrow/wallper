package com.onthecrow.wallper.core.model

/**
 * A crop area in pixels of the frame as shown, after the video's rotation
 * ([MediaInfo.displayWidth] x [MediaInfo.displayHeight]). Plain Kotlin replacement for `android.graphics.Rect`.
 */
data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
