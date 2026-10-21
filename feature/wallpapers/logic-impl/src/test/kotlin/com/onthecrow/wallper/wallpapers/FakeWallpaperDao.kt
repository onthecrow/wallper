package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.database.WallpaperDao
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

internal class FakeWallpaperDao(initial: List<WallpaperEntity> = emptyList()) : WallpaperDao {

    val rows = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<WallpaperEntity>> = rows

    override fun observeActive(): Flow<WallpaperEntity?> = rows.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun get(id: Int): WallpaperEntity = rows.value.first { it.id == id }

    override suspend fun insert(wallpaper: WallpaperEntity) {
        rows.update { list -> list + wallpaper.copy(id = (list.maxOfOrNull { it.id } ?: 0) + 1) }
    }

    override suspend fun delete(wallpaper: WallpaperEntity) {
        rows.update { list -> list.filterNot { it.id == wallpaper.id } }
    }

    override suspend fun activate(id: Int) {
        rows.update { list -> list.map { it.copy(isActive = it.id == id) } }
    }
}
