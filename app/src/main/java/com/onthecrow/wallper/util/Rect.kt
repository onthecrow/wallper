package com.onthecrow.wallper.util

import androidx.compose.ui.geometry.Rect
import com.onthecrow.wallper.domain.model.WallpaperBounds

fun Rect.toWallpaperBounds(): WallpaperBounds {
    return WallpaperBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}