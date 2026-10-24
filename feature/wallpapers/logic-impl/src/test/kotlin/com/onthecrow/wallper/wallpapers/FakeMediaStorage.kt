package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.storage.MediaStorage
import java.io.File

internal class FakeMediaStorage : MediaStorage {

    val deletedPaths = mutableListOf<String>()

    override val tempMediaFile = File("editor/media")
    override val tempThumbnailFile = File("editor/thumbnail")
    override val tempProcessedFile = File("editor/processed.mp4")

    override fun copyToTemp(uri: String): String = tempMediaFile.path
    override fun persistTempMedia(): String = "originals/media"
    override fun persistTempProcessed(): String = "originals/processed.mp4"
    override fun persistTempThumbnail(): String = "thumbnails/thumbnail"
    override fun clearTemp() = Unit

    override fun delete(path: String) {
        deletedPaths += path
    }

    /** Ten bytes a character of its path, so sizes differ. */
    override fun sizeOf(path: String): Long = path.length * 10L
}
