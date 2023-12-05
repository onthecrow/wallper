package com.onthecrow.wallper.presentation.crop.model

import com.onthecrow.wallper.core.viewmodel.BaseState

data class CropperState(val uri: String = "") : BaseState<CropperAction>()