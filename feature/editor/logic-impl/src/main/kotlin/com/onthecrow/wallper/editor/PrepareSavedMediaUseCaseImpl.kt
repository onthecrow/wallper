package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.editor.domain.PrepareSavedMediaUseCase
import com.onthecrow.wallper.editor.domain.model.EditedWallpaper
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.wallpapers.domain.GetWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class PrepareSavedMediaUseCaseImpl @Inject constructor(
    private val getWallpaperUseCase: GetWallpaperUseCase,
    private val mediaStorage: MediaStorage,
    private val dispatchersProvider: DispatchersProvider,
) : PrepareSavedMediaUseCase {

    override suspend fun invoke(wallpaperId: Int): Result<PreparedMedia> = withContext(dispatchersProvider.io) {
        runCatching {
            // An edit killed with the process may have left its scratch files behind, and an export goes there.
            mediaStorage.clearTemp()
            val wallpaper = getWallpaperUseCase(wallpaperId)
            check(wallpaper is Wallpaper.Media) { "Wallpaper $wallpaperId keeps no original to edit: $wallpaper" }
            PreparedMedia(
                path = wallpaper.originalPath,
                type = wallpaper.type,
                mediaInfo = wallpaper.mediaInfo,
                edited = EditedWallpaper(
                    id = wallpaper.id,
                    thumbnailPath = wallpaper.thumbnailPath,
                    crop = wallpaper.crop,
                    clip = wallpaper.clip,
                ),
            )
        }.onFailure(Timber::e)
    }
}
