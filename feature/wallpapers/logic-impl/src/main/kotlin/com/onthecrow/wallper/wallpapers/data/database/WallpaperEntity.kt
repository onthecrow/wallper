package com.onthecrow.wallper.wallpapers.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaType

/**
 * One row per built-in at most: [builtIn] is unique (NULLs, the media rows, don't clash in SQLite).
 *
 * A row of the user's media has [originalPath] or [processedPath]. A processed video plays as is: its clip
 * columns hold zeros, and since v9 its media size is the export's and its crops are its windows in it, one for
 * each way the screen can be turned; before, those held zeros too, like a built-in's (all of the export is
 * shown). Rows from before v4 may have both paths; they are processed videos that still keep the original.
 *
 * A group (a series) has [slideDurationMs], maybe a [transition], and placeholders in the media columns,
 * like a built-in; its members are in [WallpaperGroupItemEntity].
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
    /** [com.onthecrow.wallper.core.model.CropRect.rotation], in degrees; since v5. */
    @ColumnInfo(name = "crop_rotation", defaultValue = "0") val cropRotation: Float = 0f,
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
    /** Set for a group only: how long each of its members shows ([com.onthecrow.wallper.core.model.SlideDuration]); since v6. */
    @ColumnInfo(name = "slide_duration_ms") val slideDurationMs: Long? = null,
    /**
     * [com.onthecrow.wallper.core.model.Tint.argb] of the user's media, null without a tint; since v7. A
     * processed video has it in its frames already: there it is kept for the thumbnail.
     */
    @ColumnInfo(name = "tint") val tint: Int? = null,
    /**
     * Set for a group only: [com.onthecrow.wallper.core.model.TransitionEffect.key] of how it blends one slide
     * into the next, null for none; since v8.
     */
    @ColumnInfo(name = "transition") val transition: String? = null,
    /** How long that transition takes; null for the default. Since v8. */
    @ColumnInfo(name = "transition_ms") val transitionMs: Long? = null,
    /**
     * The crop for the screen turned the other way than [cropLeft] and the others were made for
     * ([com.onthecrow.wallper.core.model.Framing.turned]), all NULL to derive it; since v9. An optimised
     * video's is its second window in the export.
     */
    @ColumnInfo(name = "turned_crop_left") val turnedCropLeft: Int? = null,
    @ColumnInfo(name = "turned_crop_top") val turnedCropTop: Int? = null,
    @ColumnInfo(name = "turned_crop_right") val turnedCropRight: Int? = null,
    @ColumnInfo(name = "turned_crop_bottom") val turnedCropBottom: Int? = null,
    @ColumnInfo(name = "turned_crop_rotation") val turnedCropRotation: Float? = null,
)

/** [WallpaperEntity.turnedCropLeft] and the others as a crop, null unless all of them are there. */
internal val WallpaperEntity.turnedCrop: CropRect?
    get() = CropRect(
        left = turnedCropLeft ?: return null,
        top = turnedCropTop ?: return null,
        right = turnedCropRight ?: return null,
        bottom = turnedCropBottom ?: return null,
        rotation = turnedCropRotation ?: return null,
    )

/** The files of the row in device-protected storage. Empty paths are placeholders: a built-in's, a picture's thumbnail. */
internal val WallpaperEntity.files: Set<String>
    get() = setOfNotNull(originalPath, processedPath, thumbnailPath) - ""
