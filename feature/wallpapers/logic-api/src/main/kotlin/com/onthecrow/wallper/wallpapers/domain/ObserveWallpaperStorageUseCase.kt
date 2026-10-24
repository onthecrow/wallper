package com.onthecrow.wallper.wallpapers.domain

import com.onthecrow.wallper.wallpapers.domain.model.WallpaperStorage
import kotlinx.coroutines.flow.Flow

/**
 * How much of the device's storage each of the user's wallpapers takes, in the list's order; built-ins
 * take none of theirs. Reads file sizes: collect it off the main thread.
 */
interface ObserveWallpaperStorageUseCase {
    operator fun invoke(): Flow<List<WallpaperStorage>>
}
