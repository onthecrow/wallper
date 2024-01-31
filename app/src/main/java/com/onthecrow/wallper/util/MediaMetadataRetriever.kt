package com.onthecrow.wallper.util

import android.media.MediaMetadataRetriever
import kotlin.jvm.Throws

@Throws(IllegalStateException::class)
fun MediaMetadataRetriever.extractFloatMetadataUnsafe(metadataKey: Int): Float {
    return extractMetadata(metadataKey)?.toFloat()
        ?: throw IllegalStateException("Can't retrieve metadata!")
}