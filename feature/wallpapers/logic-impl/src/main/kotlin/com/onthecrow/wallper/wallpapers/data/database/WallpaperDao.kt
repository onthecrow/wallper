package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** An abstract class rather than an interface, for the group writes that take several statements. */
@Dao
internal abstract class WallpaperDao {

    /** Built-ins first, then the user's wallpapers; each in the order they were added. */
    @Query("SELECT * FROM wallpapers ORDER BY built_in IS NULL, id")
    abstract fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE is_active = 1 LIMIT 1")
    abstract fun observeActive(): Flow<WallpaperEntity?>

    @Query("SELECT * FROM wallpapers WHERE id = :id")
    abstract suspend fun get(id: Int): WallpaperEntity?

    /** Returns the new row's id. */
    @Insert
    abstract suspend fun insert(wallpaper: WallpaperEntity): Long

    @Update
    abstract suspend fun update(wallpaper: WallpaperEntity)

    /** A group's members go with it, and a wallpaper leaves every group it was in. */
    @Delete
    abstract suspend fun delete(wallpaper: WallpaperEntity)

    /** Makes [id] the only active wallpaper in one statement. */
    @Query("UPDATE wallpapers SET is_active = (id = :id)")
    abstract suspend fun activate(id: Int)

    /** Every group's members, each group's in its order. */
    @Query("SELECT * FROM wallpaper_group_items ORDER BY group_id, position")
    abstract fun observeGroupItems(): Flow<List<WallpaperGroupItemEntity>>

    /** The members of group [groupId], in its order. */
    @Query(
        """
        SELECT wallpapers.* FROM wallpaper_group_items
        JOIN wallpapers ON wallpapers.id = wallpaper_group_items.wallpaper_id
        WHERE wallpaper_group_items.group_id = :groupId
        ORDER BY wallpaper_group_items.position
        """
    )
    abstract suspend fun getGroupMembers(groupId: Int): List<WallpaperEntity>

    /** Groups left without members: their last one was deleted. */
    @Query(
        """
        DELETE FROM wallpapers WHERE slide_duration_ms IS NOT NULL
            AND id NOT IN (SELECT group_id FROM wallpaper_group_items)
        """
    )
    abstract suspend fun deleteEmptyGroups()

    /** Adds [group] with [memberIds] in their order; returns its id. */
    @Transaction
    open suspend fun insertGroup(group: WallpaperEntity, memberIds: List<Int>): Long {
        val id = insert(group)
        insertGroupItems(memberIds.toItems(id.toInt()))
        return id
    }

    /** Saves [group] with [memberIds] in their order in place of what it had. */
    @Transaction
    open suspend fun updateGroup(group: WallpaperEntity, memberIds: List<Int>) {
        update(group)
        deleteGroupItems(group.id)
        insertGroupItems(memberIds.toItems(group.id))
    }

    @Insert
    protected abstract suspend fun insertGroupItems(items: List<WallpaperGroupItemEntity>)

    @Query("DELETE FROM wallpaper_group_items WHERE group_id = :groupId")
    protected abstract suspend fun deleteGroupItems(groupId: Int)

    private fun List<Int>.toItems(groupId: Int) =
        mapIndexed { position, wallpaperId -> WallpaperGroupItemEntity(groupId, position, wallpaperId) }
}
