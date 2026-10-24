package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper

/** The wallpaper [id], or null if there is none or it is a built-in this version doesn't know. */
interface GetWallpaperUseCase {
    suspend operator fun invoke(id: Int): Wallpaper?
}
