package com.onthecrow.wallper.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private val DUMMY_BITMAP by lazy { Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) }

fun imageBitmapFromPath(path: String): ImageBitmap {
    val bitmap = BitmapFactory.decodeFile(path) ?: DUMMY_BITMAP
    return bitmap.asImageBitmap()
}