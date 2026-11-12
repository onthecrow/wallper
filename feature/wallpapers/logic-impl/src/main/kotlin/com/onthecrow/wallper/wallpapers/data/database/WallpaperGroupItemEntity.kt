package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A member of a group: [wallpaperId] shows at [position] (from 0) of the group [groupId]. Deleting either row
 * of `wallpapers` deletes this one; a member stays when its group goes. Since v6.
 */
@Entity(
    tableName = "wallpaper_group_items",
    primaryKeys = ["group_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = WallpaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["group_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = WallpaperEntity::class,
            parentColumns = ["id"],
            childColumns = ["wallpaper_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["wallpaper_id"])],
)
internal data class WallpaperGroupItemEntity(
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "wallpaper_id") val wallpaperId: Int,
)
