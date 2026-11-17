package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Tint
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import kotlinx.coroutines.flow.Flow

/**
 * Crops and trims a video into a new file, with the tint, if any, in its frames. Completes after
 * [ExportStatus.Success] or [ExportStatus.Error].
 */
internal interface VideoExporter {
    fun export(inputPath: String, outputPath: String, crop: CropRect, clip: ClipRange, tint: Tint?): Flow<ExportStatus>

    /** Whether an encoder here takes frames of [width] x [height]; an export that it doesn't falls back to less. */
    fun canEncode(width: Int, height: Int): Boolean = true
}
