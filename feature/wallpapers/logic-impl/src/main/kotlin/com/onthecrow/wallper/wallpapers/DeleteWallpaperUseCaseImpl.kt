package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.data.database.files
import com.onthecrow.wallper.wallpapers.domain.DeleteWallpaperUseCase
import com.onthecrow.wallper.wallpapers.series.SeriesPictures
import javax.inject.Inject

internal class DeleteWallpaperUseCaseImpl @Inject constructor(
    private val mediaStorage: MediaStorage,
    private val wallpapersRepository: WallpapersRepository,
    private val seriesPictures: SeriesPictures,
) : DeleteWallpaperUseCase {
    override suspend fun invoke(id: Int) {
        val wallpaper = wallpapersRepository.get(id) ?: return
        // Built-ins have no files, and the seeding would bring the row back on the next open anyway.
        if (wallpaper.builtIn != null) return
        wallpaper.files.forEach(mediaStorage::delete)
        wallpapersRepository.delete(wallpaper)
        // Its rows in groups went with it: a group it was the last member of shows nothing now.
        wallpapersRepository.deleteEmptyGroups()
        // The pictures of the groups that went, this one or those it left empty.
        seriesPictures.retain(wallpapersRepository.groupIds())
    }
}
