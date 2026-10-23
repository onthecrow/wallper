package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.WallpapersRepository
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpapersUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class ObserveWallpapersUseCaseImpl @Inject constructor(
    private val wallpapersRepository: WallpapersRepository,
) : ObserveWallpapersUseCase {
    /** In the database order: built-ins first. */
    override fun invoke(): Flow<List<Wallpaper>> =
        wallpapersRepository.observeAll().map { entities -> entities.mapNotNull { it.toDomain() } }
}
