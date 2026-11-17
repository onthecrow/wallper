package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.core.model.SlideDuration

/**
 * Saves a group of [memberIds], in their order, each shown for [slideDuration]: a new one at the end of the
 * list with no [id], which becomes the active wallpaper if the user wants new ones to; otherwise group [id]
 * in place of what it had. Returns the group's id.
 */
interface SaveWallpaperGroupUseCase {
    suspend operator fun invoke(id: Int?, memberIds: List<Int>, slideDuration: SlideDuration): Int
}
