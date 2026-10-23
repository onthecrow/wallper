package com.onthecrow.wallper.editor.cropper

/**
 * Enum for detecting which section of Composable user has initially touched
 */
internal enum class TouchRegion {
    TopLeft, TopRight, BottomLeft, BottomRight, Inside, None
}

internal fun handlesTouched(touchRegion: TouchRegion) = touchRegion == TouchRegion.TopLeft ||
        touchRegion == TouchRegion.TopRight ||
        touchRegion == TouchRegion.BottomLeft ||
        touchRegion == TouchRegion.BottomRight
