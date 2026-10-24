package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper

/**
 * Replaces what the user's wallpaper [id] shows with [wallpaper]. It keeps its place in the list and stays
 * active if it was. Files the old version had and the new one doesn't are deleted.
 */
interface UpdateWallpaperUseCase {
    suspend operator fun invoke(id: Int, wallpaper: NewWallpaper)
}
