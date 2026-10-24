package com.onthecrow.wallper.editor

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.domain.DiscardPreparedMediaUseCase
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.domain.PrepareSavedMediaUseCase
import com.onthecrow.wallper.editor.domain.ProcessingInfoMemory
import com.onthecrow.wallper.editor.domain.SaveWallpaperUseCase
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = EditorViewModel.Factory::class)
internal class EditorViewModel @AssistedInject constructor(
    @Assisted private val destination: EditorDestination,
    reducer: EditorReducer,
    private val prepareMediaUseCase: PrepareMediaUseCase,
    private val prepareSavedMediaUseCase: PrepareSavedMediaUseCase,
    private val saveWallpaperUseCase: SaveWallpaperUseCase,
    private val discardPreparedMediaUseCase: DiscardPreparedMediaUseCase,
    private val processingInfoMemory: ProcessingInfoMemory,
    private val navigator: Navigator,
) : BaseViewModel<EditorEvent, EditorState, EditorReducer>(EditorState(), reducer) {

    @AssistedFactory
    interface Factory {
        fun create(destination: EditorDestination): EditorViewModel
    }

    /** The media couldn't be prepared or played: there is nothing to edit. Touched only on the main thread. */
    private var isMediaBroken = false

    init {
        eventFlow.onEach { event ->
            when (event) {
                // A failed export can be saved again; a file that can't be read or played can't.
                EditorEvent.OnErrorDismiss -> if (isMediaBroken) navigator.navigateBack()
                is EditorEvent.OnPreviewFailed -> isMediaBroken = true
                else -> Unit
            }
        }.launchIn(viewModelScope)

        // The reducer decides when a save starts: once the media is ready, one at a time.
        state.map { it.save }.distinctUntilChanged().filterNotNull().onEach(::save).launchIn(viewModelScope)

        // Shown is seen, for later editors too.
        state.map { it.processingInfo != null }.distinctUntilChanged().filter { it }
            .onEach { processingInfoMemory.markSeen() }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            onEvent(EditorEvent.OnProcessingInfoSeenLoaded(processingInfoMemory.isSeen()))
            // After the flag: whether the switch waits for the explanation depends on it.
            if ((destination as? EditorDestination.Edit)?.optimise == true) onEvent(EditorEvent.OnOptimisedToggle)
        }
        prepareMedia()
    }

    /** The editor is gone: its scratch copy of the media and what an export made of it go too. */
    override fun onCleared() {
        discardPreparedMediaUseCase()
    }

    private fun prepareMedia() {
        viewModelScope.launch {
            val prepared = when (destination) {
                is EditorDestination.New -> prepareMediaUseCase(destination.uri)
                is EditorDestination.Edit -> prepareSavedMediaUseCase(destination.wallpaperId)
            }
            prepared.fold(
                onSuccess = { media -> onEvent(EditorEvent.OnMediaPrepared(media)) },
                onFailure = { error ->
                    isMediaBroken = true
                    onEvent(EditorEvent.OnExportStatusChanged(ExportStatus.Error(error)))
                },
            )
        }
    }

    private fun save(request: SaveRequest) {
        saveWallpaperUseCase(request.media, request.crop, request.trim, request.isOptimised)
            .onEach { status ->
                onEvent(EditorEvent.OnExportStatusChanged(status))
                if (status is ExportStatus.Success) {
                    navigator.navigateBack()
                }
            }
            .catch { error ->
                Timber.e(error, "Can't save the wallpaper")
                onEvent(EditorEvent.OnExportStatusChanged(ExportStatus.Error(error)))
            }
            .launchIn(viewModelScope)
    }
}
