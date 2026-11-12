package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.wallpapers.data.database.WallpaperDao
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.data.database.WallpaperGroupItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The database in memory, foreign keys included: deleting a wallpaper deletes its group items. */
internal class FakeWallpaperDao(initial: List<WallpaperEntity> = emptyList()) : WallpaperDao() {

    val rows = MutableStateFlow(initial)
    val groupItems = MutableStateFlow(emptyList<WallpaperGroupItemEntity>())

    // Ordered like the query: built-ins first, then by id.
    override fun observeAll(): Flow<List<WallpaperEntity>> =
        rows.map { all -> all.sortedWith(compareBy({ it.builtIn == null }, { it.id })) }

    override fun observeActive(): Flow<WallpaperEntity?> = rows.map { list -> list.firstOrNull { it.isActive } }

    override suspend fun get(id: Int): WallpaperEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(wallpaper: WallpaperEntity): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.update { list -> list + wallpaper.copy(id = id) }
        return id.toLong()
    }

    override suspend fun update(wallpaper: WallpaperEntity) {
        rows.update { list -> list.map { if (it.id == wallpaper.id) wallpaper else it } }
    }

    override suspend fun delete(wallpaper: WallpaperEntity) {
        rows.update { list -> list.filterNot { it.id == wallpaper.id } }
        groupItems.update { items -> items.filterNot { it.groupId == wallpaper.id || it.wallpaperId == wallpaper.id } }
    }

    override suspend fun activate(id: Int) {
        rows.update { list -> list.map { it.copy(isActive = it.id == id) } }
    }

    override fun observeGroupItems(): Flow<List<WallpaperGroupItemEntity>> =
        groupItems.map { items -> items.sortedWith(compareBy({ it.groupId }, { it.position })) }

    override suspend fun getGroupMembers(groupId: Int): List<WallpaperEntity> =
        groupItems.value.filter { it.groupId == groupId }.sortedBy { it.position }
            .mapNotNull { item -> rows.value.firstOrNull { it.id == item.wallpaperId } }

    override suspend fun deleteEmptyGroups() {
        rows.update { list ->
            list.filterNot { row -> row.slideDurationMs != null && groupItems.value.none { it.groupId == row.id } }
        }
    }

    override suspend fun insertGroupItems(items: List<WallpaperGroupItemEntity>) {
        groupItems.update { it + items }
    }

    override suspend fun deleteGroupItems(groupId: Int) {
        groupItems.update { items -> items.filterNot { it.groupId == groupId } }
    }
}
