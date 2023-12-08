package com.onthecrow.wallper.presentation.crop.model

import com.onthecrow.wallper.core.viewmodel.BaseState

data class CropperState(
    val uri: String = "",
    val originalFilePath: String = "",
    val isVideo: Boolean = false,
    val thumbnailPath: String = "",
) : BaseState<CropperAction>()