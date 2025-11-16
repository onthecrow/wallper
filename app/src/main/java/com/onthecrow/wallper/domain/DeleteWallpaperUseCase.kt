package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.StorageRepository
import com.onthecrow.wallper.data.WallpapersRepository
import javax.inject.Inject

class DeleteWallpaperUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
    private val wallpapersRepository: WallpapersRepository,
) {
    suspend operator fun invoke(id: Int) {
        val wallpaper = wallpapersRepository.getWallpaper(id)
        val originalUri = wallpaper.originalUri
        val processedUri = wallpaper.processedUri
        val thumbnailUri = wallpaper.thumbnailUri
        storageRepository.deleteFile(originalUri)
        storageRepository.deleteFile(processedUri)
        storageRepository.deleteFile(thumbnailUri)
        wallpapersRepository.deleteWallpaper(wallpaper)
    }
}