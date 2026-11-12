package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal data class WallpapersState(
    val items: List<WallpaperItem> = emptyList(),
    /**
     * Previews rendered so far, null for a built-in this device can't render. Kept apart from [items] so that
     * they survive list updates.
     */
    val builtInPreviews: Map<BuiltInWallpaper, String?> = emptyMap(),
    /** The series' pictures made so far, by group id; kept apart from [items] like [builtInPreviews]. */
    val seriesPictures: Map<Int, String> = emptyMap(),
    val error: String = "",
    /** The wallpaper whose deletion waits for the user's confirmation. */
    val deleteCandidate: Int? = null,
    /** The «+» asks whether to add one wallpaper or a series. */
    val isAddDialogShown: Boolean = false,
) : State {

    /**
     * What each series' picture is to be made of, by group id: its cover's pictures, once every built-in among
     * them has its preview (or is known to have none).
     */
    fun seriesCovers(): Map<Int, List<String?>> = items
        .filter { item -> item.isGroup && item.cover.all { it.builtIn == null || it.builtIn in builtInPreviews } }
        .associate { group -> group.id to group.cover.map { it.picturePath } }
}
