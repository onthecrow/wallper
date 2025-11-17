package com.onthecrow.wallper.presentation.wallpapers.models

import com.onthecrow.wallper.data.WallpaperEntity

class Wallpaper(
    val id: Int,
    val picturePath: String,
    val isActive: Boolean,
    val videoUri: String,
) {

    companion object {
        fun mapFromDomain(domainEntity: WallpaperEntity): Wallpaper {
            return Wallpaper(
                picturePath = if (domainEntity.isVideo) domainEntity.thumbnailUri else domainEntity.originalUri,
                isActive = domainEntity.isActive,
                id = domainEntity.uid,
                videoUri = domainEntity.originalUri,
            )
        }
    }
}
