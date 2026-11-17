package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.GetWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import javax.inject.Inject

internal class GetWallpaperUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
) : GetWallpaperUseCase {
    override suspend fun invoke(id: Int): Wallpaper? = wallpapersRepository.getWallpaper(id)
}
