package com.onthecrow.wallper.editor.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The picture at [path], stretched over its bounds, which have its proportions. Decoded as stored, without
 * EXIF orientation, like the media info's size; at most [MAX_SIDE] pixels on its longer side.
 */
@Composable
internal fun PictureImage(path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodePicture(path) }
    }
    bitmap?.let { Image(it, contentDescription = null, modifier = modifier, contentScale = ContentScale.FillBounds) }
}

private fun decodePicture(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_SIDE) sampleSize *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    if (bitmap == null) Timber.e("Can't decode the picture to crop")
    return bitmap?.asImageBitmap()
}

private const val MAX_SIDE = 2048
