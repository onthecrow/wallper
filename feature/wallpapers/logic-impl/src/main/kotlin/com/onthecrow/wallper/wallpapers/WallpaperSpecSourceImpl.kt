package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.engine.WallpaperSpecSource
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.mapper.toSpec
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class WallpaperSpecSourceImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
) : WallpaperSpecSource {
    override suspend fun slides(ids: List<Int>): List<WallpaperSpec> {
        val wallpapers = wallpapersRepository.observeWallpapers().first().associateBy { it.id }
        return ids.mapNotNull { id -> wallpapers[id]?.takeUnless { it is Wallpaper.Group }?.toSpec() }
    }
}
