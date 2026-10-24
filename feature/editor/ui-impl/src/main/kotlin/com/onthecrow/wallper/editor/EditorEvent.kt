package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.mvi.Event
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia

internal sealed interface EditorEvent : Event {
    // User actions
    data class OnSaveClick(val crop: CropRect) : EditorEvent
    data class OnTrimChange(val trim: ClipRange) : EditorEvent
    /** Puts the start of the kept part at the playhead, at [positionMs]. */
    data class OnSetTrimStartClick(val positionMs: Long) : EditorEvent
    /** Puts the end of the kept part at the playhead, at [positionMs]. */
    data class OnSetTrimEndClick(val positionMs: Long) : EditorEvent
    data object OnPlayPauseClick : EditorEvent
    /** Original / optimised. */
    data object OnOptimisedToggle : EditorEvent
    data object OnProcessingInfoClick : EditorEvent
    data object OnProcessingInfoConfirm : EditorEvent
    /** Back or a tap outside the explanation: a switch to optimised that waits for it doesn't happen. */
    data object OnProcessingInfoDismiss : EditorEvent
    data object OnErrorDismiss : EditorEvent

    // Internal events
    data class OnProcessingInfoSeenLoaded(val isSeen: Boolean) : EditorEvent
    data class OnMediaPrepared(val media: PreparedMedia) : EditorEvent
    data class OnExportStatusChanged(val status: ExportStatus) : EditorEvent
    /** The preview has played the kept part to its end and stopped there. */
    data object OnPlaybackEnded : EditorEvent
    /** The preview can't play the media: the file may be gone or unreadable. */
    data class OnPreviewFailed(val error: Throwable) : EditorEvent
}
