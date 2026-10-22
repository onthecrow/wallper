package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.CropRect

/** A crop rect in normalized device coordinates: both axes from -1 to 1, y pointing up. */
internal data class NDCCoords(val left: Float, val right: Float, val bottom: Float, val top: Float) {
    companion object {
        /** [rect] is in pixels of the upright [frameWidth] x [frameHeight] frame. */
        fun fromRect(rect: CropRect, frameWidth: Float, frameHeight: Float) = NDCCoords(
            left = 2 * rect.left / frameWidth - 1,
            right = 2 * rect.right / frameWidth - 1,
            bottom = 1 - 2 * rect.bottom / frameHeight,
            top = 1 - 2 * rect.top / frameHeight,
        )
    }
}
