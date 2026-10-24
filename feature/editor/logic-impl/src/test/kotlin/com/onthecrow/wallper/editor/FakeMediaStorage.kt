package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.storage.MediaStorage
import java.io.File

/** Records what reaches permanent storage and what is deleted; the scratch files are never touched. */
internal class FakeMediaStorage : MediaStorage {

    val persisted = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    var clears = 0

    override val tempMediaFile = File("editor/media")
    override val tempThumbnailFile = File("editor/thumbnail")
    override val tempProcessedFile = File("editor/processed.mp4")

    override fun copyToTemp(uri: String) = tempMediaFile.path
    override fun persistTempMedia() = MEDIA.also(persisted::add)
    override fun persistTempProcessed() = PROCESSED.also(persisted::add)
    override fun persistTempThumbnail() = THUMBNAIL.also(persisted::add)

    override fun clearTemp() {
        clears++
    }

    override fun delete(path: String) {
        // The export clears its scratch output first: that is not a permanent file going.
        if (path != tempProcessedFile.path) deleted += path
    }

    companion object {
        const val MEDIA = "originals/media"
        const val PROCESSED = "originals/processed.mp4"
        const val THUMBNAIL = "thumbnails/thumbnail"
    }
}
