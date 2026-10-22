package com.onthecrow.wallper.core.model

/**
 * Facts about a media file that rendering and cropping depend on. [width] and [height] are as stored,
 * before [rotation] (degrees clockwise) is applied for display.
 */
data class MediaInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationMs: Long,
) {
    /** Width of the frame as shown, after [rotation]. Crop rects are in these coordinates. */
    val displayWidth: Int get() = if (rotation % 180 == 0) width else height

    /** Height of the frame as shown, after [rotation]. */
    val displayHeight: Int get() = if (rotation % 180 == 0) height else width
}
