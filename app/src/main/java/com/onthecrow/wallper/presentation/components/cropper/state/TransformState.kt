package com.onthecrow.wallper.presentation.components.cropper.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * State of the pan, zoom and rotation. Allows to change zoom, pan via [Animatable]
 * objects' [Animatable.animateTo], [Animatable.snapTo].
 */
@Stable
open class TransformState(
    internal val imageSize: IntSize,
    val containerSize: IntSize,
    val drawAreaSize: IntSize,
) {

    var drawAreaRect: Rect by mutableStateOf(
        Rect(
            offset = Offset(
                x = ((containerSize.width - drawAreaSize.width) / 2).toFloat(),
                y = ((containerSize.height - drawAreaSize.height) / 2).toFloat()
            ),
            size = Size(drawAreaSize.width.toFloat(), drawAreaSize.height.toFloat())
        )
    )
}
