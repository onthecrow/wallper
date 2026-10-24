package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.MediaType

/**
 * One row per built-in at most: [builtIn] is unique (NULLs, the media rows, don't clash in SQLite).
 *
 * A row of the user's media has [originalPath] or [processedPath]. A processed video plays as is: its crop,
 * clip and media columns hold zeros, like a built-in's. Rows from before v4 may have both paths; they are
 * processed videos that still keep the original.
 */
@Entity(tableName = "wallpapers", indices = [Index(value = ["built_in"], unique = true)])
internal data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "type") val type: MediaType,
    /** The media as it was picked; null since v4 for a processed video, which keeps no original. */
    @ColumnInfo(name = "original_path") val originalPath: String?,
    /** The video exported cropped and trimmed ("additional processing"). */
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

/** The files of the row in device-protected storage. Empty paths are placeholders: a built-in's, a picture's thumbnail. */
internal val WallpaperEntity.files: Set<String>
    get() = setOfNotNull(originalPath, processedPath, thumbnailPath) - ""
