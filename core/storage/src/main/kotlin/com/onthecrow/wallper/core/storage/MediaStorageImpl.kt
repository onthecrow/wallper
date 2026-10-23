package com.onthecrow.wallper.core.storage

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MediaStorageImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : MediaStorage {

    // All device-protected, like the database: the wallpaper reads the media and the thumbnail (its
    // colours) before the first unlock. External storage isn't there yet then, and may be missing at all.
    private val deviceProtectedContext = context.createDeviceProtectedStorageContext()
    private val tempDir = File(deviceProtectedContext.filesDir, "editor")
    private val thumbnailsDir = File(deviceProtectedContext.filesDir, "thumbnails")
    private val originalsDir = deviceProtectedContext.filesDir

    override val tempMediaFile = File(tempDir, "media")
    override val tempThumbnailFile = File(tempDir, "thumbnail")
    override val tempProcessedFile = File(tempDir, "processed.mp4")

    override fun copyToTemp(uri: String): String? = runCatching {
        tempDir.mkdirs()
        checkNotNull(context.contentResolver.openInputStream(uri.toUri())) { "Can't open $uri" }.use { input ->
            tempMediaFile.outputStream().use { output -> input.copyTo(output) }
        }
        tempMediaFile.absolutePath
    }.onFailure(Timber::e).getOrNull()

    override fun persistTempMedia(): String =
        tempMediaFile.copyTo(File(originalsDir, randomName()), overwrite = true).absolutePath
            .also { Timber.d("Tmp file saved: $it") }

    override fun persistTempProcessed(): String =
        tempProcessedFile.copyTo(File(originalsDir, "${randomName()}.mp4"), overwrite = true).absolutePath

    override fun persistTempThumbnail(): String? = runCatching {
        thumbnailsDir.mkdirs()
        tempThumbnailFile.copyTo(File(thumbnailsDir, randomName()), overwrite = true).absolutePath
    }.onFailure(Timber::e).getOrNull()

    override fun clearTemp() {
        if (!tempDir.deleteRecursively()) Timber.e("Can't delete all of $tempDir")
    }

    override fun delete(path: String) {
        runCatching {
            File(path).run { if (exists()) delete() }
        }.onFailure(Timber::e)
    }

    private fun randomName() = UUID.randomUUID().toString()
}
