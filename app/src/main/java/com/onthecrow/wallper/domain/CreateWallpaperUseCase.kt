package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.StorageRepository
import com.onthecrow.wallper.data.WallpapersRepository
import com.onthecrow.wallper.domain.model.TempFile
import com.onthecrow.wallper.domain.model.WallpaperBounds
import javax.inject.Inject

class CreateWallpaperUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
    private val wallpapersRepository: WallpapersRepository,
) {
    suspend operator fun invoke(wallpaperBounds: WallpaperBounds, tempFile: TempFile) {
        var thumbnailPath: String? = null
        if (tempFile.isVideo) {
            thumbnailPath = storageRepository.saveTempThumbnail()
        }
        val filePath = storageRepository.saveTempFile()
        wallpapersRepository.saveWallpaper(
            filePath ?: "",
            thumbnailPath ?: "",
            false,
            wallpaperBounds,
            tempFile.isVideo,
        )
    }
}