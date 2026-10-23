package com.onthecrow.wallper.editor.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Reads facts about picked media. Blocking calls: use from a background dispatcher. */
internal class MediaInspector @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun isPicture(uri: String): Boolean {
        return openFileDescriptor(uri)?.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
        } != null
    }

    fun isVideo(uri: String): Boolean {
        return try {
            openFileDescriptor(uri)?.use { fileDescriptor ->
                with(MediaExtractor()) {
                    setDataSource(fileDescriptor.fileDescriptor)
                    (0 until trackCount).mapNotNull { getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
                        .also { release() }
                }
            }?.any { it.contains(MIME_VIDEO) } ?: false
        } catch (error: Throwable) {
            Timber.e(error)
            false
        }
    }

    /** Writes the first frame of the video at [uri] into [target] as JPEG. */
    fun writeVideoThumbnail(uri: String, target: File) {
        // MediaMetadataRetriever is AutoCloseable only since API 29, so no use {} here.
        val retriever = MediaMetadataRetriever()
        try {
            openFileDescriptor(uri).use { fileDescriptor -> retriever.setDataSource(fileDescriptor?.fileDescriptor) }
            val bitmap = retriever.getFrameAtTime(0)
            target.parentFile?.mkdirs()
            target.outputStream().use { outputStream ->
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }
        } finally {
            retriever.release()
        }
    }

    fun readMediaInfo(path: String, type: MediaType): MediaInfo = when (type) {
        MediaType.VIDEO -> readVideoInfo(path)
        MediaType.IMAGE -> readImageInfo(path)
    }

    private fun readVideoInfo(path: String): MediaInfo {
        val mmr = MediaMetadataRetriever()
        val rotation: String?
        val width: String?
        val height: String?
        val duration: String?
        try {
            mmr.setDataSource(context, Uri.encode(path).toUri())
            rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        } finally {
            mmr.release()
        }
        return MediaInfo(
            width = width?.toInt() ?: 0,
            height = height?.toInt() ?: 0,
            rotation = rotation?.toInt() ?: 0,
            durationMs = duration?.toLong() ?: 0L,
        )
    }

    private fun readImageInfo(path: String): MediaInfo {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return MediaInfo(width = options.outWidth, height = options.outHeight, rotation = 0, durationMs = 0L)
    }

    private fun openFileDescriptor(uri: String): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri.toUri(), "r")
        } catch (error: Throwable) {
            Timber.e(error)
            null
        }
    }

    companion object {
        private const val MIME_VIDEO = "video"
    }
}
