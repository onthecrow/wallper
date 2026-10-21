package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.engine.ActiveWallpaperSource
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.mapper.toDomain
import com.onthecrow.wallper.wallpapers.mapper.toSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Feeds the wallpaper service from the wallpapers database. */
internal class ActiveWallpaperSourceImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
) : ActiveWallpaperSource {
    override val activeWallpaperFlow: Flow<WallpaperSpec?> =
        wallpapersRepository.observeActive().map { entity -> entity?.toDomain()?.toSpec() }
}
