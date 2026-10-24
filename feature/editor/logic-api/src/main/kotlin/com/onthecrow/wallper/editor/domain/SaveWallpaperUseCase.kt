package com.onthecrow.wallper.editor.domain

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.flow.Flow

/**
 * Saves what the editor made of [PreparedMedia]: a new wallpaper, or the new version of the one it edits.
 *
 * Without [process] the original is kept and the wallpaper crops and trims it while it plays. With it a
 * video is exported cropped and trimmed, and only the export is kept: the wallpaper can't be edited again.
 * Files reach permanent storage only once the wallpaper is saved; the scratch ones stay until
 * [DiscardPreparedMediaUseCase].
 */
interface SaveWallpaperUseCase {
    operator fun invoke(
        media: PreparedMedia,
        crop: CropRect,
        clip: ClipRange,
        process: Boolean,
    ): Flow<ExportStatus>
}
