package com.onthecrow.wallper.presentation.wallpapers.models

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.onthecrow.wallper.data.WallpaperEntity

class Wallpaper(
    val id: Int,
    val bitmap: ImageBitmap,
    val isActive: Boolean,
) {

    companion object {
        fun mapFromDomain(domainEntity: WallpaperEntity): Wallpaper {
            return Wallpaper(
                bitmap = BitmapFactory.decodeFile(domainEntity.thumbnailUri).asImageBitmap(),
                isActive = domainEntity.isActive,
                id = domainEntity.uid
            )
        }
    }
}
