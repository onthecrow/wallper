package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.MediaType

@Entity(tableName = "wallpapers")
internal data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "type") val type: MediaType,
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "processed_path") val processedPath: String?,
    @ColumnInfo(name = "thumbnail_path") val thumbnailPath: String,
    @ColumnInfo(name = "crop_left") val cropLeft: Int,
    @ColumnInfo(name = "crop_top") val cropTop: Int,
    @ColumnInfo(name = "crop_right") val cropRight: Int,
    @ColumnInfo(name = "crop_bottom") val cropBottom: Int,
    @ColumnInfo(name = "clip_start_ms") val clipStartMs: Long,
    @ColumnInfo(name = "clip_end_ms") val clipEndMs: Long,
    @ColumnInfo(name = "media_width") val mediaWidth: Int,
    @ColumnInfo(name = "media_height") val mediaHeight: Int,
    @ColumnInfo(name = "media_rotation") val mediaRotation: Int,
    @ColumnInfo(name = "media_duration_ms") val mediaDurationMs: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    /**
     * [BuiltInWallpaper.key] for rows of built-in wallpapers, null for the user's media.
     * The media columns of a built-in row hold placeholders, see [SeedBuiltInWallpapersCallback].
     */
    @ColumnInfo(name = "built_in") val builtIn: String? = null,
)
