package com.onthecrow.wallper.wallpapers.domain

interface ActivateWallpaperUseCase {
    suspend operator fun invoke(id: Int)
}
