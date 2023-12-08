package com.onthecrow.wallper.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import okio.use
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val appFolder = context.getExternalFilesDir(null)
    private val thumbnailsFolder = File("$appFolder/thumbnails/")
    private val originalsFolder = File("$appFolder/originals/")
    private val tempFolder = File("$appFolder/temp/")

    init {
        createFolders()
    }

    fun makeThumbnailTemp(uri: String): String {
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(getFileDescriptor(uri)?.fileDescriptor)
                val bitmap = retriever.getFrameAtTime(0)
                File("$tempFolder/tempThumbnail").apply {
                    outputStream().use { outputStream ->
                        bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                }.absolutePath
            }
        } catch (error: Throwable) {
            Timber.e(error)
            ""
        }
    }

    fun copyFileTemp(uri: String): String {
        return try {
            context.contentResolver.openInputStream(Uri.parse(uri))
        } catch (error: Throwable) {
            Timber.e(error)
            null
        }?.use { inputStream ->
            File("$tempFolder/tempFile").apply {
                outputStream().use { outputStream ->
                    val buffer = ByteArray(4000)
                    while (inputStream.available() > 0) {
                        inputStream.read(buffer)
                        outputStream.write(buffer)
                    }
                }
            }.absolutePath
        } ?: ""
    }

    fun isFilePicture(uri: String): Boolean {
        return getFileDescriptor(uri)?.use {
            BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
        } != null
    }

    fun isFileVideo(uri: String): Boolean {
        return try {
            getFileDescriptor(uri)?.let { fileDescriptor ->
                with(MediaExtractor()) {
                    setDataSource(fileDescriptor.fileDescriptor)
                    sequence {
                        for (i in 0 until trackCount) {
                            yield(getTrackFormat(i).getString(MediaFormat.KEY_MIME))
                        }
                    }.filterNotNull()
                }
            }?.any { it.contains(MIME_VIDEO) } ?: false
        } catch (error: Throwable) {
            Timber.e(error)
            false
        }
    }

    private fun getFileDescriptor(
        uri: String,
        mode: OpenFileMode = OpenFileMode.READ
    ): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), mode.value)
        } catch (error: Throwable) {
            Timber.e(error)
            null
        }
    }

    private fun createFolders() {
        // TODO replace MainScope()
        MainScope().launch {
            if (!thumbnailsFolder.exists()) {
                thumbnailsFolder.mkdir()
            }
            if (!originalsFolder.exists()) {
                originalsFolder.mkdir()
            }
            if (!tempFolder.exists()) {
                tempFolder.mkdir()
            }
        }
    }

    companion object {
        private const val MIME_VIDEO = "video"
    }
}

private enum class OpenFileMode(val value: String) {
    READ("r"), WRITE("w"), READWRITE("rw")
}