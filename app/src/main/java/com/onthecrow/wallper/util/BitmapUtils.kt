package com.onthecrow.wallper.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap


private val DUMMY_BITMAP by lazy { Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888) }

fun imageBitmapFromPath(path: String): ImageBitmap {
    return (BitmapFactory.decodeFile(path) ?: DUMMY_BITMAP).asImageBitmap()
}

fun croppedBitmap(dstWidth: Int, dstHeight: Int, path: String, rect: Rect): Bitmap {
    return BitmapFactory.decodeFile(path).let { srcBitmap ->
        val croppedBitmap = Bitmap.createBitmap(srcBitmap, rect.left, rect.top, rect.width(), rect.height())
        srcBitmap.recycle()
        Bitmap.createScaledBitmap(croppedBitmap, dstWidth, dstHeight, true).also {
            croppedBitmap.recycle()
        }
    }
}