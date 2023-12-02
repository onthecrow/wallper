package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.WallpapersRepository
import javax.inject.Inject

class ActivateWallpaperUseCase @Inject constructor(
    private val wallpapersRepository: WallpapersRepository
) {
    suspend operator fun invoke(id: Int) {
       wallpapersRepository.activateWallpaper(id)
    }
}