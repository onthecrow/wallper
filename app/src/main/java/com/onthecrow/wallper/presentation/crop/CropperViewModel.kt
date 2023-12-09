package com.onthecrow.wallper.presentation.crop

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.viewmodel.BaseViewModel
import com.onthecrow.wallper.domain.CreateWallpaperUseCase
import com.onthecrow.wallper.domain.GetScreenResolutionUseCase
import com.onthecrow.wallper.domain.PrepareFileUseCase
import com.onthecrow.wallper.domain.model.TempFile
import com.onthecrow.wallper.presentation.crop.model.CropperAction
import com.onthecrow.wallper.presentation.crop.model.CropperEvent
import com.onthecrow.wallper.presentation.crop.model.CropperState
import com.onthecrow.wallper.util.imageBitmapFromPath
import com.onthecrow.wallper.util.toWallpaperBounds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CropperViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val prepareFileUseCase: PrepareFileUseCase,
    private val getScreenResolutionUseCase: GetScreenResolutionUseCase,
    private val createWallpaperUseCase: CreateWallpaperUseCase,
) : BaseViewModel<CropperState, CropperAction, CropperEvent>(
    CropperState(checkNotNull(savedStateHandle[CROPPER_SCREEN_NAV_ARGUMENT_URI]))
) {
    init {
        prepareFile()
    }

    override fun sendEvent(uiEvent: CropperEvent) {
        when (uiEvent) {
            is CropperEvent.CreateWallpaper -> createWallpaper(uiEvent.rect)
        }
    }

    private fun createWallpaper(rect: Rect) {
        viewModelScope.launch(Dispatchers.IO) {
            createWallpaperUseCase(
                rect.toWallpaperBounds(),
                TempFile(uiState.value.originalFilePath, uiState.value.isVideo, uiState.value.thumbnailPath),
            )
            performAction(CropperAction.NavigateToWallpapersScreen)
        }
    }

    private fun prepareFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val preparedFile = prepareFileUseCase(uiState.value.uri)
            val screenResolution = getScreenResolutionUseCase()
            updateUiState {
                copy(
                    originalFilePath = preparedFile.originalFilePath,
                    isVideo = preparedFile.isVideo,
                    thumbnailPath = preparedFile.thumbnailPath,
                    screenWidth = screenResolution.width,
                    screenHeight = screenResolution.height,
                    bitmap = imageBitmapFromPath(
                        if (preparedFile.isVideo) {
                            preparedFile.thumbnailPath
                        } else {
                            preparedFile.originalFilePath
                        }
                    ),
                )
            }
        }
    }
}