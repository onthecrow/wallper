package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.data.database.files
import com.onthecrow.wallper.wallpapers.domain.UpdateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.mapper.toEntity
import javax.inject.Inject

internal class UpdateWallpaperUseCaseImpl @Inject constructor(
    private val mediaStorage: MediaStorage,
    private val wallpapersRepository: WallpapersRepository,
) : UpdateWallpaperUseCase {
    override suspend fun invoke(id: Int, wallpaper: NewWallpaper) {
        val old = checkNotNull(wallpapersRepository.get(id)) { "No wallpaper $id" }
        check(old.builtIn == null) { "Built-in wallpaper $id has no media to replace" }
        val new = wallpaper.toEntity(id = id, isActive = old.isActive)
        wallpapersRepository.update(new)
        // Only now: until the update the wallpaper may still be showing them.
        (old.files - new.files).forEach(mediaStorage::delete)
    }
}
