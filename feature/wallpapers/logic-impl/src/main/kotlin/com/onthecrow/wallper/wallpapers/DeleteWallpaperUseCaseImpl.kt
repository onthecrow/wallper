package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.data.database.files
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.DeleteWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.series.SeriesPictures
import javax.inject.Inject

internal class DeleteWallpaperUseCaseImpl @Inject constructor(
    private val mediaStorage: MediaStorage,
    private val wallpapersRepository: WallpapersRepository,
    private val seriesPictures: SeriesPictures,
    private val activateWallpaperUseCase: ActivateWallpaperUseCase,
) : DeleteWallpaperUseCase {
    override suspend fun invoke(id: Int) {
        val wallpaper = wallpapersRepository.get(id) ?: return
        // Built-ins have no files, and the seeding would bring the row back on the next open anyway.
        if (wallpaper.builtIn != null) return
        val heir = heirOfActiveSeries(id)
        wallpaper.files.forEach(mediaStorage::delete)
        wallpapersRepository.delete(wallpaper)
        // Its rows in groups went with it: a group left with one member would only be that member now.
        wallpapersRepository.deleteTooSmallGroups()
        heir?.let { activateWallpaperUseCase(it) }
        // The pictures of the groups that went, this one or those it left too small.
        seriesPictures.retain(wallpapersRepository.groupIds())
    }

    /**
     * What an active series that goes with [id] leaves: too few members for a series, the first of which
     * the screen is given to, as the series showed it. Null where the active wallpaper stays as it is.
     */
    private suspend fun heirOfActiveSeries(id: Int): Int? {
        val series = wallpapersRepository.getActive() as? Wallpaper.Group ?: return null
        val left = series.members.map { it.id }.filter { it != id }
        return left.takeIf { it.size < Wallpaper.Group.MIN_MEMBERS }?.firstOrNull()
    }
}
