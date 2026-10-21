package com.onthecrow.wallper.wallpapers.data

import com.onthecrow.wallper.wallpapers.data.database.WallpaperDao
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class WallpapersRepository @Inject constructor(
    private val wallpaperDao: WallpaperDao,
) {
    fun observeAll(): Flow<List<WallpaperEntity>> = wallpaperDao.observeAll()

    fun observeActive(): Flow<WallpaperEntity?> = wallpaperDao.observeActive()

    suspend fun get(id: Int): WallpaperEntity = wallpaperDao.get(id)

    suspend fun insert(wallpaper: WallpaperEntity) = wallpaperDao.insert(wallpaper)

    suspend fun delete(wallpaper: WallpaperEntity) = wallpaperDao.delete(wallpaper)

    suspend fun activate(id: Int) = wallpaperDao.activate(id)
}
