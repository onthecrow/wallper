package com.onthecrow.wallper.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity
data class WallpaperEntity(
    @ColumnInfo(name = "original_uri") val originalUri: String,
    @ColumnInfo(name = "thumbnails_uri") val thumbnailUri: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
) {
    @Ignore
    var bitmap: ImageBitmap? = null
}