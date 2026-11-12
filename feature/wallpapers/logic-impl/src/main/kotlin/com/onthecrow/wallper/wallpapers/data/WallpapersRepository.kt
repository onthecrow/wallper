package com.onthecrow.wallper.wallpapers.data

import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.wallpapers.data.database.WallpaperDao
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.mapper.groupEntity
import com.onthecrow.wallper.wallpapers.mapper.isGroup
import com.onthecrow.wallper.wallpapers.mapper.toDomain
import com.onthecrow.wallper.wallpapers.mapper.toGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

internal class WallpapersRepository @Inject constructor(
    private val wallpaperDao: WallpaperDao,
) {
    fun observeAll(): Flow<List<WallpaperEntity>> = wallpaperDao.observeAll()

    /** Every wallpaper in the list's order, groups with their members. */
    fun observeWallpapers(): Flow<List<Wallpaper>> =
        combine(wallpaperDao.observeAll(), wallpaperDao.observeGroupItems()) { rows, items -> rows.toDomain(items) }

    suspend fun get(id: Int): WallpaperEntity? = wallpaperDao.get(id)

    /** Wallpaper [id], a group with its members. */
    suspend fun getWallpaper(id: Int): Wallpaper? {
        val row = wallpaperDao.get(id) ?: return null
        if (!row.isGroup) return row.toDomain()
        return row.toGroup(wallpaperDao.getGroupMembers(id).mapNotNull { it.toDomain() })
    }

    /** Returns the new wallpaper's id. */
    suspend fun insert(wallpaper: WallpaperEntity): Int = wallpaperDao.insert(wallpaper).toInt()

    suspend fun update(wallpaper: WallpaperEntity) = wallpaperDao.update(wallpaper)

    suspend fun delete(wallpaper: WallpaperEntity) = wallpaperDao.delete(wallpaper)

    suspend fun activate(id: Int) = wallpaperDao.activate(id)

    /** Returns the new group's id. */
    suspend fun insertGroup(memberIds: List<Int>, slideDuration: SlideDuration): Int =
        wallpaperDao.insertGroup(groupEntity(slideDuration = slideDuration), memberIds).toInt()

    /** Group [id], [isActive] as it was. */
    suspend fun updateGroup(id: Int, memberIds: List<Int>, slideDuration: SlideDuration, isActive: Boolean) =
        wallpaperDao.updateGroup(groupEntity(id, slideDuration, isActive), memberIds)

    suspend fun deleteEmptyGroups() = wallpaperDao.deleteEmptyGroups()

    /** The ids of every group there is. */
    suspend fun groupIds(): Set<Int> = wallpaperDao.observeAll().first().filter { it.isGroup }.map { it.id }.toSet()
}
