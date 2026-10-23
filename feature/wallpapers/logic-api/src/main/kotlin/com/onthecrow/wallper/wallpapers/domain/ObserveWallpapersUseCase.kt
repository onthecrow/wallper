package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.flow.Flow

/** Built-in wallpapers first, then the user's ones in the order they were added. */
interface ObserveWallpapersUseCase {
    operator fun invoke(): Flow<List<Wallpaper>>
}
