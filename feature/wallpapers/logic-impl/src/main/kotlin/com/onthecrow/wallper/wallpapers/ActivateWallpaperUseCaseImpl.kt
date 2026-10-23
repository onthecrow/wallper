package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import javax.inject.Inject

internal class ActivateWallpaperUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
    private val activations: WallpaperActivations,
) : ActivateWallpaperUseCase {
    // Before the write: the service then knows that the database update following it is a pick.
    override suspend fun invoke(id: Int) {
        activations.notifyActivated()
        wallpapersRepository.activate(id)
    }
}
