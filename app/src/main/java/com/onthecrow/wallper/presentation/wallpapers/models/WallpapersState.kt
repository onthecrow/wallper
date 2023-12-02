package com.onthecrow.wallper.presentation.wallpapers.models

import com.onthecrow.wallper.core.viewmodel.BaseState
import com.onthecrow.wallper.data.WallpaperEntity

data class WallpapersState(
    val items: List<Wallpaper> = listOf(),
    val error: String = "",
    override val actions: List<WallpapersAction> = listOf(),
) : BaseState<WallpapersAction>() {
    override fun updateActions(newActions: List<WallpapersAction>) = copy(actions = newActions)
}