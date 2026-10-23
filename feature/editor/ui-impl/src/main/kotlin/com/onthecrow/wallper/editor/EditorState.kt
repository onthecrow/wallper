package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia

internal data class EditorState(
    /** Null until the picked file is copied and inspected. */
    val media: PreparedMedia? = null,
    /** The part of the video the wallpaper keeps: all of it once it is prepared, `0..0` if its length is unknown. */
    val trim: ClipRange = ClipRange(startMs = 0, endMs = 0),
    /** Whether the user wants the preview to play. Dragging the trim bar pauses it without changing this. */
    val isPlaying: Boolean = true,
    val isAdditionalProcessing: Boolean = false,
    val exportStatus: ExportStatus? = null,
) : State
