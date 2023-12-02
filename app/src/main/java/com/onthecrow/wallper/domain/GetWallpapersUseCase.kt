package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.WallpaperEntity
import com.onthecrow.wallper.data.WallpapersRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetWallpapersUseCase @Inject constructor(
    private val wallpapersRepository: WallpapersRepository
) {
    operator fun invoke(): Flow<List<WallpaperEntity>> {
        return wallpapersRepository.getWallpapers()
    }
}