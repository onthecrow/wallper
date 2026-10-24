package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.mvi.Reducer
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import javax.inject.Inject

internal class EditorReducer @Inject constructor() : Reducer<EditorState, EditorEvent> {
    override suspend fun reduce(state: EditorState, event: EditorEvent): EditorState =
        when (event) {
            is EditorEvent.OnTrimChange -> state.copy(trim = event.trim)
            // The buttons are off when the kept part would get too short, but a click may come in the same frame.
            is EditorEvent.OnSetTrimStartClick -> state.trim.withStartAt(event.positionMs)?.let { state.copy(trim = it) } ?: state
            is EditorEvent.OnSetTrimEndClick -> state.trim.withEndAt(event.positionMs)?.let { state.copy(trim = it) } ?: state
            EditorEvent.OnPlayPauseClick -> state.copy(isPlaying = !state.isPlaying)
            EditorEvent.OnPlaybackEnded -> state.copy(isPlaying = false)
            EditorEvent.OnOptimisedToggle -> when {
                state.isOptimised -> state.copy(isOptimised = false)
                // The first switch to optimised waits for the explanation.
                !state.isProcessingInfoSeen -> state.showProcessingInfo(optimisesOnConfirm = true)
                else -> state.copy(isOptimised = true)
            }
            EditorEvent.OnProcessingInfoClick -> state.showProcessingInfo(optimisesOnConfirm = false)
            EditorEvent.OnProcessingInfoConfirm -> state.copy(
                processingInfo = null,
                isOptimised = state.isOptimised || state.processingInfo?.optimisesOnConfirm == true,
            )
            is EditorEvent.OnSaveClick -> state.requestSave(event.crop)
            EditorEvent.OnProcessingInfoDismiss -> state.copy(processingInfo = null)
            is EditorEvent.OnProcessingInfoSeenLoaded ->
                state.copy(isProcessingInfoSeen = state.isProcessingInfoSeen || event.isSeen)
            // A new wallpaper keeps the whole video until the user moves a handle; an edited one starts from its trim.
            is EditorEvent.OnMediaPrepared -> state.copy(
                media = event.media,
                trim = event.media.edited?.clip ?: ClipRange(startMs = 0, endMs = event.media.mediaInfo.durationMs),
            )
            // A failed save can be tried again; a successful one closes the editor.
            is EditorEvent.OnExportStatusChanged -> state.copy(
                exportStatus = event.status,
                save = state.save.takeUnless { event.status is ExportStatus.Error },
            )
            // The same dialog as an export error; closing it leaves the editor.
            is EditorEvent.OnPreviewFailed -> state.copy(exportStatus = ExportStatus.Error(event.error))
            EditorEvent.OnErrorDismiss -> state.copy(exportStatus = null)
        }

    /** Starts a save once the media is ready, one at a time. */
    private fun EditorState.requestSave(crop: CropRect): EditorState {
        val media = media ?: return this
        if (save != null) return this
        return copy(save = SaveRequest(media, crop, trim, isOptimised))
    }

    /** Shown is seen: a later switch to optimised doesn't wait for it. */
    private fun EditorState.showProcessingInfo(optimisesOnConfirm: Boolean) =
        copy(processingInfo = ProcessingInfo(optimisesOnConfirm), isProcessingInfoSeen = true)
}
