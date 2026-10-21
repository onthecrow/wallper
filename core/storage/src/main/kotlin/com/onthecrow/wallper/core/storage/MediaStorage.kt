package com.onthecrow.wallper.core.storage

import java.io.File

/**
 * Where wallpaper media lives. The wallpaper service runs in direct boot, so everything it reads
 * is kept in device-protected storage.
 */
interface MediaStorage {

    /** Scratch copy of the media picked in the editor. */
    val tempMediaFile: File

    /** Scratch thumbnail of the media picked in the editor. */
    val tempThumbnailFile: File

    /** Copies a content [uri] into [tempMediaFile]. Returns its path, or null if the uri can't be read. */
    fun copyToTemp(uri: String): String?

    /** Copies [tempMediaFile] to permanent storage and returns the new path. */
    fun persistTempMedia(): String

    /** Copies [tempThumbnailFile] to permanent storage. Returns the new path, or null if there is none. */
    fun persistTempThumbnail(): String?

    fun delete(path: String)
}
