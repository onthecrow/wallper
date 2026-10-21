package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.mapper.toEntity
import javax.inject.Inject

internal class SaveWallpaperUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
) : SaveWallpaperUseCase {
    override suspend fun invoke(wallpaper: NewWallpaper) = wallpapersRepository.insert(wallpaper.toEntity())
}
