package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.designsystem.component.colorpicker.HsvColor
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
    /** Back to the trim, the tint and the choice the editor opened with; the crop resets itself in the screen. */
    data object OnResetClick : EditorEvent
    /** The tint button: its picker and switch come in place of the trim bar and the save row, or go again. */
    data object OnTintClick : EditorEvent
    /** Back while the tint's picker is on screen: it goes. */
    data object OnTintClose : EditorEvent
    /** The bar's back: out of the editor, without saving. */
    data object OnCloseClick : EditorEvent
    /** The tint's switch. */
    data object OnTintToggle : EditorEvent
    /** A touch on the picker: it also switches the tint on, or the colour would change unseen. */
    data class OnTintColorChange(val color: HsvColor) : EditorEvent
    /** The picture snapped level, or its centre to the frame's middle: a tick. */
    data object OnCropSnapped : EditorEvent
    /** Cancel in the export's dialog: the save stops, and the editor stays as it was. */
    data object OnExportCancel : EditorEvent

    // Internal events
    data class OnProcessingInfoSeenLoaded(val isSeen: Boolean) : EditorEvent
    data class OnMediaPrepared(val media: PreparedMedia) : EditorEvent
    data class OnExportStatusChanged(val status: ExportStatus) : EditorEvent
    /** The preview has played the kept part to its end and stopped there. */
    data object OnPlaybackEnded : EditorEvent
    /** The preview can't play the media: the file may be gone or unreadable. */
    data class OnPreviewFailed(val error: Throwable) : EditorEvent
}
