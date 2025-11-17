package com.onthecrow.wallper.presentation.components.cropper

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.debugInspectorInfo
import com.onthecrow.wallper.presentation.components.cropper.gesture.detectMotionEventsAsList
import com.onthecrow.wallper.presentation.components.cropper.gesture.detectTransformGestures
import com.onthecrow.wallper.presentation.components.cropper.model.CropData
import com.onthecrow.wallper.presentation.components.cropper.state.CropState
import com.onthecrow.wallper.presentation.components.cropper.state.cropData
import kotlinx.coroutines.launch

/**
 * Modifier that zooms in or out of Composable set to. This zoom modifier has option
 * to move back to bounds with an animation or option to have fling gesture when user removes
 * from screen while velocity is higher than threshold to have smooth touch effect.
 *
 * @param keys are used for [Modifier.pointerInput] to restart closure when any keys assigned
 * change
 * empty space on sides or edges of parent.
 * @param cropState State of the zoom that contains option to set initial, min, max zoom,
 * enabling rotation, pan or zoom and contains current [CropData]
 * event propagations. Also contains [Rect] of visible area based on pan, zoom and rotation
 * @param onGestureStart callback to to notify gesture has started and return current
 * [CropData]  of this modifier
 * @param onGesture callback to notify about ongoing gesture and return current
 * [CropData]  of this modifier
 * @param onGestureEnd callback to notify that gesture finished return current
 * [CropData]  of this modifier
 */
fun Modifier.crop(
    vararg keys: Any?,
    cropState: CropState,
    onDown: ((CropData) -> Unit)? = null,
    onMove: ((CropData) -> Unit)? = null,
    onUp: ((CropData) -> Unit)? = null,
    onGestureStart: ((CropData) -> Unit)? = null,
    onGesture: ((CropData) -> Unit)? = null,
    onGestureEnd: ((CropData) -> Unit)? = null
) = composed(

    factory = {

        LaunchedEffect(key1 = cropState){
            cropState.init()
        }

        val coroutineScope = rememberCoroutineScope()

        val transformModifier = Modifier.pointerInput(*keys) {
            detectTransformGestures(
                consume = false,
                onGestureStart = {
                    onGestureStart?.invoke(cropState.cropData)
                },
                onGestureEnd = {
                    coroutineScope.launch {
                        cropState.onGestureEnd {
                            onGestureEnd?.invoke(cropState.cropData)
                        }
                    }
                },
                onGesture = { centroid, pan, zoom, rotate, mainPointer, pointerList ->

                    coroutineScope.launch {
                        cropState.onGesture(
                            centroid = centroid,
                            panChange = pan,
                            zoomChange = zoom,
                            rotationChange = rotate,
                            mainPointer = mainPointer,
                            changes = pointerList
                        )
                    }
                    onGesture?.invoke(cropState.cropData)
                    mainPointer.consume()
                }
            )
        }

        val tapModifier = Modifier.pointerInput(*keys) {
            detectTapGestures(
                onDoubleTap = { offset: Offset ->
                    coroutineScope.launch {
                        cropState.onDoubleTap(
                            offset = offset,
                        ) {
                            onGestureEnd?.invoke(cropState.cropData)
                        }
                    }
                }
            )
        }

        val touchModifier = Modifier.pointerInput(*keys) {
            detectMotionEventsAsList(
                onDown = {
                    coroutineScope.launch {
                        cropState.onDown(it)
                        onDown?.invoke(cropState.cropData)
                    }
                },
                onMove = {
                    coroutineScope.launch {
                        cropState.onMove(it)
                        onMove?.invoke(cropState.cropData)
                    }
                },
                onUp = {
                    coroutineScope.launch {
                        cropState.onUp(it)
                        onUp?.invoke(cropState.cropData)
                    }
                }
            )
        }

        this.then(
            clipToBounds()
                .then(tapModifier)
                .then(transformModifier)
                .then(touchModifier)
        )
    },
    inspectorInfo = debugInspectorInfo {
        name = "crop"
        // add name and value of each argument
        properties["keys"] = keys
        properties["onDown"] = onGestureStart
        properties["onMove"] = onGesture
        properties["onUp"] = onGestureEnd
    }
)
