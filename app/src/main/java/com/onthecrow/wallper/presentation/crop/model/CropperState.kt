package com.onthecrow.wallper.presentation.crop.model

import androidx.compose.ui.graphics.ImageBitmap
import com.onthecrow.wallper.core.viewmodel.BaseState
import com.onthecrow.wallper.util.imageBitmapFromPath

data class CropperState(
    val uri: String = "",
    val originalFilePath: String = "",
    val isVideo: Boolean = false,
    val thumbnailPath: String = "",
    val screenWidth: Float = 1f,
    val screenHeight: Float = 1f,
    val bitmap: ImageBitmap = imageBitmapFromPath(""),
    override val actions: List<CropperAction> = listOf(),
) : BaseState<CropperAction>() {
    override fun updateActions(newActions: List<CropperAction>) = copy(actions = newActions)
}