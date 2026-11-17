package com.onthecrow.wallper.editor.domain

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.Tint
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import kotlinx.coroutines.flow.Flow

/**
 * Saves what the editor made of [PreparedMedia]: a new wallpaper, or the new version of the one it edits.
 * A new one becomes the active wallpaper if the user wants it so (`AppSettings.activateNewWallpapers`).
 *
 * Without [process] the original is kept and the wallpaper crops and trims it while it plays. With it a
 * video is exported cropped and trimmed, and only the export is kept: the wallpaper can't be edited again.
 * The [framing] has a crop for each way the screen can be turned; an export holds both, or, where no encoder
 * takes that size, the crop alone.
 * A [tint] is drawn over the original each time it is shown, and goes into the frames of an export.
 * Files reach permanent storage only once the wallpaper is saved; the scratch ones stay until
 * [DiscardPreparedMediaUseCase].
 */
interface SaveWallpaperUseCase {
    operator fun invoke(
        media: PreparedMedia,
        framing: Framing,
        clip: ClipRange,
        process: Boolean,
        tint: Tint?,
    ): Flow<ExportStatus>
}
