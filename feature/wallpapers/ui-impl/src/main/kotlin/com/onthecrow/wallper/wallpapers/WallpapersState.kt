package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal data class WallpapersState(
    val items: List<WallpaperItem> = emptyList(),
    /** Previews rendered so far. Kept apart from [items] so that they survive list updates. */
    val builtInPreviews: Map<BuiltInWallpaper, String> = emptyMap(),
    val error: String = "",
) : State
