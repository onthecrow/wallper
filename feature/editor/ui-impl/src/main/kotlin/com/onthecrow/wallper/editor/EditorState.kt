package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia

internal data class EditorState(
    /** Null until the picked file is copied and inspected. */
    val media: PreparedMedia? = null,
    /** Frame to show while the range slider is dragged; null while the clip plays. */
    val seekPosition: Float? = null,
    val isAdditionalProcessing: Boolean = false,
    val timeLineRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val exportStatus: ExportStatus? = null,
) : State
