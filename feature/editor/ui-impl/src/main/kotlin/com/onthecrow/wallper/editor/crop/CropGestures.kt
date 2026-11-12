package com.onthecrow.wallper.editor.crop

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs

/**
 * Drag, pinch and turn, like `detectTransformGestures`, plus the start (first finger down) and the end (last
 * finger up) of each gesture. Moves start past the touch slop. Turning starts only once the fingers have
 * turned by [ROTATION_SLOP_DEGREES] in this gesture, so that a pinch doesn't tilt the picture by accident.
 */
internal suspend fun PointerInputScope.detectCropGestures(
    onStart: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    onEnd: () -> Unit,
) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false)
    onStart()
    val touchSlop = viewConfiguration.touchSlop
    var zoom = 1f
    var pan = Offset.Zero
    var rotation = 0f
    var pastTouchSlop = false
    var rotating = false
    try {
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()
                rotation += rotationChange
                if (!rotating && abs(rotation) > ROTATION_SLOP_DEGREES) rotating = true
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    pastTouchSlop = zoomMotion > touchSlop || panMotion > touchSlop || rotating
                }
                if (pastTouchSlop) {
                    val appliedRotation = if (rotating) rotationChange else 0f
                    if (appliedRotation != 0f || zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange, appliedRotation)
                    }
                    event.changes.fastForEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
    } finally {
        onEnd()
    }
}

/** How far the fingers must turn before the picture turns with them. */
private const val ROTATION_SLOP_DEGREES = 5f
