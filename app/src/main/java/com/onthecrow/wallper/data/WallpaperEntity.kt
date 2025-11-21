package com.onthecrow.wallper.data

import android.graphics.Rect
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class WallpaperEntity(
    @ColumnInfo(name = "original_uri") val originalUri: String,
    @ColumnInfo(name = "thumbnails_uri") val thumbnailUri: String,
    @ColumnInfo(name = "processed_uri") val processedUri: String,
    @ColumnInfo(name = "shown_rect") val shownRect: Rect,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "is_processed") val isProcessed: Boolean,
    @ColumnInfo(name = "is_video") val isVideo: Boolean,
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    val startPosition: Long,
    val endPosition: Long,
)