package com.onthecrow.wallper.wallpapers.domain

/**
 * Removes the record and all of its files. A group goes alone, its members stay; a group left without members
 * goes too. Built-in wallpapers can't be deleted: for them it does nothing.
 */
interface DeleteWallpaperUseCase {
    suspend operator fun invoke(id: Int)
}
