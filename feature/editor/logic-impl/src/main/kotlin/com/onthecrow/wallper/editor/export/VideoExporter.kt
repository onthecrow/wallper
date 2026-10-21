package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import kotlinx.coroutines.flow.Flow

/** Crops and trims a video into a new file. Completes after [ExportStatus.Success] or [ExportStatus.Error]. */
internal interface VideoExporter {
    fun export(inputPath: String, outputPath: String, crop: CropRect, clip: ClipRange): Flow<ExportStatus>
}
