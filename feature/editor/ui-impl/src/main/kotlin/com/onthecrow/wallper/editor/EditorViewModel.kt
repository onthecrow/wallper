package com.onthecrow.wallper.editor

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.domain.CreateWallpaperUseCase
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
internal class EditorViewModel @AssistedInject constructor(
    @Assisted private val destination: EditorDestination,
    reducer: EditorReducer,
    private val prepareMediaUseCase: PrepareMediaUseCase,
    private val createWallpaperUseCase: CreateWallpaperUseCase,
    private val navigator: Navigator,
) : BaseViewModel<EditorEvent, EditorState, EditorReducer>(EditorState(), reducer) {

    @AssistedFactory
    interface Factory {
        fun create(destination: EditorDestination): EditorViewModel
    }

    /** The media couldn't be prepared: there is nothing to edit. Touched only on the main thread. */
    private var isMediaBroken = false

    init {
        eventFlow.onEach { event ->
            when (event) {
                is EditorEvent.OnSaveClick -> createWallpaper(event.crop)
                // A failed export can be saved again; a file that can't be read can't.
                EditorEvent.OnErrorDismiss -> if (isMediaBroken) navigator.navigateBack()
                else -> Unit
            }
        }.launchIn(viewModelScope)

        prepareMedia()
    }

    private fun prepareMedia() {
        viewModelScope.launch {
            prepareMediaUseCase(destination.uri).fold(
                onSuccess = { media -> onEvent(EditorEvent.OnMediaPrepared(media)) },
                onFailure = { error ->
                    isMediaBroken = true
                    onEvent(EditorEvent.OnExportStatusChanged(ExportStatus.Error(error)))
                },
            )
        }
    }

    private fun createWallpaper(crop: CropRect) {
        val currentState = state.value
        val media = currentState.media ?: return
        val duration = media.mediaInfo.durationMs
        val clip = ClipRange(
            startMs = (currentState.timeLineRange.start * duration).toLong(),
            endMs = (currentState.timeLineRange.endInclusive * duration).toLong(),
        )
        createWallpaperUseCase(media, crop, clip, currentState.isAdditionalProcessing)
            .onEach { status ->
                onEvent(EditorEvent.OnExportStatusChanged(status))
                if (status is ExportStatus.Success) {
                    navigator.navigateBack()
                }
            }
            .catch { Timber.e(it) }
            .launchIn(viewModelScope)
    }
}
