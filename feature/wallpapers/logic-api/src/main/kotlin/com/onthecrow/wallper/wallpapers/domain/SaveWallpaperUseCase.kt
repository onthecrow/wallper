package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper

interface SaveWallpaperUseCase {
    suspend operator fun invoke(wallpaper: NewWallpaper)
}
