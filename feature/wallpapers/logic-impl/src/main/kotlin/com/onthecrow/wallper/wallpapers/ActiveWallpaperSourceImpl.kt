package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.engine.ActiveWallpaperSource
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.mapper.toSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Feeds the wallpaper service from the wallpapers database: an active group is a series of its members.
 * An active built-in unknown to this version gives null.
 */
internal class ActiveWallpaperSourceImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
    activations: WallpaperActivations,
) : ActiveWallpaperSource {
    override val activeWallpaperFlow: Flow<WallpaperSpec?> =
        wallpapersRepository.observeWallpapers().map { wallpapers -> wallpapers.firstOrNull { it.isActive }?.toSpec() }

    override val activations: Flow<Unit> = activations.flow
}
