package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.storage.MediaStorage
import java.io.File

internal class FakeMediaStorage : MediaStorage {

    val deletedPaths = mutableListOf<String>()

    override val tempMediaFile = File("temp/media")
    override val tempThumbnailFile = File("temp/thumbnail")

    override fun copyToTemp(uri: String): String = tempMediaFile.path
    override fun persistTempMedia(): String = "originals/media"
    override fun persistTempThumbnail(): String = "thumbnails/thumbnail"

    override fun delete(path: String) {
        deletedPaths += path
    }
}
