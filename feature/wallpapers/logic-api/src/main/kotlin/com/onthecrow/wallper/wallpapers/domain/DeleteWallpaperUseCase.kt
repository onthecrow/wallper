package com.onthecrow.wallper.wallpapers.domain

/** Removes the record and all of its files. */
interface DeleteWallpaperUseCase {
    suspend operator fun invoke(id: Int)
}
