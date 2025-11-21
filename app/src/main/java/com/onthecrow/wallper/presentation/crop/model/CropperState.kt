package com.onthecrow.wallper.presentation.crop.model

import androidx.compose.ui.unit.IntSize
import com.onthecrow.wallper.core.viewmodel.BaseState

data class CropperState(
    val uri: String = "",
    val originalFilePath: String = "",
    val isVideo: Boolean = false,
    val thumbnailPath: String = "",
    val screenWidth: Float = 1f,
    val screenHeight: Float = 1f,
    val error: Throwable? = null,
    val seekPosition: Float? = null,
    val isAdditionalProcessing: Boolean = false,
    val timeLineRange: ClosedFloatingPointRange<Float> = 0f..1f,
    override val actions: List<CropperAction> = listOf(),
    val videoSize: IntSize = IntSize(0, 0),
) : BaseState<CropperAction>() {
    override fun updateActions(newActions: List<CropperAction>) = copy(actions = newActions)
}