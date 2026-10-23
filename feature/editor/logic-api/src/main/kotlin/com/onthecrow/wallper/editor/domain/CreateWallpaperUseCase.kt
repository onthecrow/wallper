package com.onthecrow.wallper.editor.domain

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.flow.Flow

/**
 * Copies the prepared media to permanent storage and saves the wallpaper.
 * With [preCrop] a video is first exported cropped and trimmed, so the service can play it without GL cropping.
 * Files reach permanent storage only once the wallpaper is saved; the scratch ones stay until
 * [DiscardPreparedMediaUseCase].
 */
interface CreateWallpaperUseCase {
    operator fun invoke(
        media: PreparedMedia,
        crop: CropRect,
        clip: ClipRange,
        preCrop: Boolean,
    ): Flow<ExportStatus>
}
