package com.onthecrow.wallper.presentation.crop

import android.content.Context
import android.graphics.Rect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.viewmodel.BaseViewModel
import com.onthecrow.wallper.crop.VideoCroppingStatus
import com.onthecrow.wallper.domain.CreateWallpaperUseCase
import com.onthecrow.wallper.domain.GetScreenResolutionUseCase
import com.onthecrow.wallper.domain.PrepareFileUseCase
import com.onthecrow.wallper.domain.model.TempFile
import com.onthecrow.wallper.presentation.crop.model.CropperAction
import com.onthecrow.wallper.presentation.crop.model.CropperEvent
import com.onthecrow.wallper.presentation.crop.model.CropperState
import com.onthecrow.wallper.util.MetadataUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CropperViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val prepareFileUseCase: PrepareFileUseCase,
    private val getScreenResolutionUseCase: GetScreenResolutionUseCase,
    private val createWallpaperUseCase: CreateWallpaperUseCase,
) : BaseViewModel<CropperState, CropperAction, CropperEvent>(
    CropperState(checkNotNull(savedStateHandle[CROPPER_SCREEN_NAV_ARGUMENT_URI]),)
) {

    val conversionChannel = Channel<VideoCroppingStatus>()

    init {
        prepareFile()
    }

    override fun sendEvent(uiEvent: CropperEvent) {
        when (uiEvent) {
            is CropperEvent.CreateWallpaper -> createWallpaper(
                Rect(
                    uiEvent.rect.left.toInt(),
                    uiEvent.rect.top.toInt(),
                    uiEvent.rect.right.toInt(),
                    uiEvent.rect.bottom.toInt(),
                ),
            )
            CropperEvent.ShowAdditionalProcessingInfo -> {}
            is CropperEvent.TimeLineRangeChanged -> updateUiState { copy(timeLineRange = uiEvent.newRange) }
            CropperEvent.ToggleAdditionalProcessing -> updateUiState { copy(isAdditionalProcessing = !isAdditionalProcessing) }
        }
    }

    private fun createWallpaper(rect: Rect) {
        viewModelScope.launch(Dispatchers.Main) {
            createWallpaperUseCase.invoke(
                rect,
                TempFile(
                    uiState.value.originalFilePath,
                    uiState.value.isVideo,
                    uiState.value.thumbnailPath
                ),
                uiState.value.isAdditionalProcessing,
            )
                .onEach {
                    conversionChannel.send(it)
                    if (it is VideoCroppingStatus.Success) {
                        performAction(CropperAction.NavigateToWallpapersScreen)
                    }
                }
                .catch { Timber.e(it) }
                .collect { Timber.d(it.toString()) }
        }
    }

    private fun prepareFile() {
        viewModelScope.launch(Dispatchers.IO) {
            val preparedFile = prepareFileUseCase(uiState.value.uri)
            val screenResolution = getScreenResolutionUseCase()
            val metadata = MetadataUtils.getVideoMetadata(context, preparedFile.originalFilePath)
            updateUiState {
                copy(
                    originalFilePath = preparedFile.originalFilePath,
                    isVideo = preparedFile.isVideo,
                    thumbnailPath = preparedFile.thumbnailPath,
                    screenWidth = screenResolution.width,
                    screenHeight = screenResolution.height,
                    videoSize = IntSize(metadata.width, metadata.height),
                    error = preparedFile.error,
                )
            }
            preparedFile.error?.let {
                conversionChannel.send(VideoCroppingStatus.Error(it))
            }
        }
    }
}