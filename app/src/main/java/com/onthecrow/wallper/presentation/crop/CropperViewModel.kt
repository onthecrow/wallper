package com.onthecrow.wallper.presentation.crop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.viewmodel.BaseViewModel
import com.onthecrow.wallper.domain.PrepareFileUseCase
import com.onthecrow.wallper.presentation.crop.model.CropperAction
import com.onthecrow.wallper.presentation.crop.model.CropperEvent
import com.onthecrow.wallper.presentation.crop.model.CropperState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CropperViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    prepareFileUseCase: PrepareFileUseCase,
) : BaseViewModel<CropperState, CropperAction, CropperEvent>(
    CropperState(checkNotNull(savedStateHandle[CROPPER_SCREEN_NAV_ARGUMENT_URI]))
) {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val preparedFile = prepareFileUseCase(uiState.value.uri)
            updateUiState {
                copy(
                    originalFilePath = preparedFile.originalFilePath,
                    isVideo = preparedFile.isVideo,
                    thumbnailPath = preparedFile.thumbnailPath,
                )
            }

        }
    }

    override fun sendEvent(uiEvent: CropperEvent) {
        TODO("Not yet implemented")
    }
}