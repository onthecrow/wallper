package com.onthecrow.wallper.presentation.crop

import androidx.lifecycle.SavedStateHandle
import com.onthecrow.wallper.core.viewmodel.BaseViewModel
import com.onthecrow.wallper.presentation.crop.model.CropperAction
import com.onthecrow.wallper.presentation.crop.model.CropperEvent
import com.onthecrow.wallper.presentation.crop.model.CropperState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CropperViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : BaseViewModel<CropperState, CropperAction, CropperEvent>(
    CropperState(checkNotNull(savedStateHandle[CROPPER_SCREEN_NAV_ARGUMENT_URI]))
) {
    override fun sendEvent(uiEvent: CropperEvent) {
        TODO("Not yet implemented")
    }
}