package com.onthecrow.wallper.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.onthecrow.wallper.service.VideoMetadata
import androidx.core.net.toUri

object MetadataUtils {
    fun getVideoMetadata(context: Context, path: String): VideoMetadata {
        val mmr = MediaMetadataRetriever()
//        mmr.setDataSource(path)
        mmr.setDataSource(context, Uri.encode(path).toUri())
        val rotation = mmr.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )
        val width = mmr.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
        )
        val height = mmr.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
        )
        mmr.release()
        return VideoMetadata(
            rotation?.toInt() ?: 0,
            width?.toInt() ?: 0,
            height?.toInt() ?: 0,
        )
    }
}