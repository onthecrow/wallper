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
 *
 * An optimised video from before schema v9 doesn't know its export's size, which a screen turned the other
 * way needs to derive its window: it is read from the file ([frameSizes]).
 */
internal class ActiveWallpaperSourceImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
    activations: WallpaperActivations,
    private val frameSizes: VideoFrameSizes = VideoFrameSizes.NONE,
) : ActiveWallpaperSource {
    override val activeWallpaperFlow: Flow<WallpaperSpec?> =
        wallpapersRepository.observeWallpapers().map { wallpapers ->
            wallpapers.firstOrNull { it.isActive }?.toSpec()?.withExportSizes()
        }

    override val activations: Flow<Unit> = activations.flow

    private suspend fun WallpaperSpec.withExportSizes(): WallpaperSpec = when (this) {
        is WallpaperSpec.ProcessedVideo -> if (frameWidth > 0 && frameHeight > 0) {
            this
        } else {
            frameSizes.of(path)?.let { copy(frameWidth = it.width, frameHeight = it.height) } ?: this
        }
        is WallpaperSpec.Series -> copy(slides = slides.map { it.withExportSizes() })
        else -> this
    }
}
