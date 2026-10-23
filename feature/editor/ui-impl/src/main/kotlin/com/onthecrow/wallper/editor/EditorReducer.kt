package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class EditorReducer @Inject constructor() : Reducer<EditorState, EditorEvent> {
    override suspend fun reduce(state: EditorState, event: EditorEvent): EditorState =
        when (event) {
            // The handle that moved decides which frame the preview shows.
            is EditorEvent.OnTimeLineRangeChange -> if (event.range.start != state.timeLineRange.start) {
                state.copy(seekPosition = event.range.start, timeLineRange = event.range)
            } else {
                state.copy(seekPosition = event.range.endInclusive, timeLineRange = event.range)
            }
            EditorEvent.OnTimeLineRangeChangeFinished -> state.copy(seekPosition = null)
            EditorEvent.OnAdditionalProcessingToggle -> state.copy(isAdditionalProcessing = !state.isAdditionalProcessing)
            is EditorEvent.OnMediaPrepared -> state.copy(media = event.media)
            is EditorEvent.OnExportStatusChanged -> state.copy(exportStatus = event.status)
            EditorEvent.OnErrorDismiss -> state.copy(exportStatus = null)
            is EditorEvent.OnSaveClick,
            EditorEvent.OnAdditionalProcessingInfoClick -> state
        }
}
