package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import javax.inject.Inject

internal class ActivateWallpaperUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
) : ActivateWallpaperUseCase {
    override suspend fun invoke(id: Int) = wallpapersRepository.activate(id)
}
