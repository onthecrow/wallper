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
    data object OnPlayPauseClick : EditorEvent
    data object OnAdditionalProcessingToggle : EditorEvent
    data object OnAdditionalProcessingInfoClick : EditorEvent
    data object OnErrorDismiss : EditorEvent

    // Internal events
    data class OnMediaPrepared(val media: PreparedMedia) : EditorEvent
    data class OnExportStatusChanged(val status: ExportStatus) : EditorEvent
    /** The preview can't play the media: the file may be gone or unreadable. */
    data class OnPreviewFailed(val error: Throwable) : EditorEvent
}
