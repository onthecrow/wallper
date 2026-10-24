package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.data.database.files
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpaperStorageUseCase
import com.onthecrow.wallper.wallpapers.domain.model.WallpaperStorage
import com.onthecrow.wallper.wallpapers.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class ObserveWallpaperStorageUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
    private val mediaStorage: MediaStorage,
) : ObserveWallpaperStorageUseCase {
    override fun invoke(): Flow<List<WallpaperStorage>> = wallpapersRepository.observeAll().map { entities ->
        entities.filter { it.builtIn == null }.mapNotNull { entity ->
            entity.toDomain()?.let { wallpaper -> WallpaperStorage(wallpaper, entity.files.sumOf(mediaStorage::sizeOf)) }
        }
    }
}
