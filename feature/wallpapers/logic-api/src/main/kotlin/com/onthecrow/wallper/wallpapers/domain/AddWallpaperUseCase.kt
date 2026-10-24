package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper

/** Adds a record for [NewWallpaper], at the end of the list, and returns its id. */
interface AddWallpaperUseCase {
    suspend operator fun invoke(wallpaper: NewWallpaper): Int
}
