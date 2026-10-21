package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.wallpapers.model.WallpaperItem

internal data class WallpapersState(
    val items: List<WallpaperItem> = emptyList(),
    val error: String = "",
) : State
