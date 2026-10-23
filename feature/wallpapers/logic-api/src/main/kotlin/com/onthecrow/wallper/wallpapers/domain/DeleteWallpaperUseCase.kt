package com.onthecrow.wallper.wallpapers.domain

/** Removes the record and all of its files. Built-in wallpapers can't be deleted: for them it does nothing. */
interface DeleteWallpaperUseCase {
    suspend operator fun invoke(id: Int)
}
