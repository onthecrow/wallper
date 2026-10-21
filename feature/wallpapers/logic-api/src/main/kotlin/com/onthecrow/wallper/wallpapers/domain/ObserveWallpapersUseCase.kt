package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import kotlinx.coroutines.flow.Flow

interface ObserveWallpapersUseCase {
    operator fun invoke(): Flow<List<Wallpaper>>
}
