package com.onthecrow.wallper.editor.domain

import com.onthecrow.wallper.editor.domain.model.PreparedMedia

/**
 * Opens the original of saved wallpaper [wallpaperId] for editing, with its crop and trim. The original is
 * edited where it is, without a copy. Fails for a wallpaper that keeps no original.
 */
interface PrepareSavedMediaUseCase {
    suspend operator fun invoke(wallpaperId: Int): Result<PreparedMedia>
}
