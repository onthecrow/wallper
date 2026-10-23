package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class EditorReducer @Inject constructor() : Reducer<EditorState, EditorEvent> {
    override suspend fun reduce(state: EditorState, event: EditorEvent): EditorState =
        when (event) {
            is EditorEvent.OnTrimChange -> state.copy(trim = event.trim)
            EditorEvent.OnPlayPauseClick -> state.copy(isPlaying = !state.isPlaying)
            EditorEvent.OnAdditionalProcessingToggle -> state.copy(isAdditionalProcessing = !state.isAdditionalProcessing)
            // The whole video is kept until the user moves a handle.
            is EditorEvent.OnMediaPrepared -> state.copy(
                media = event.media,
                trim = ClipRange(startMs = 0, endMs = event.media.mediaInfo.durationMs),
            )
            is EditorEvent.OnExportStatusChanged -> state.copy(exportStatus = event.status)
            EditorEvent.OnErrorDismiss -> state.copy(exportStatus = null)
            is EditorEvent.OnSaveClick,
            EditorEvent.OnAdditionalProcessingInfoClick -> state
        }
}
