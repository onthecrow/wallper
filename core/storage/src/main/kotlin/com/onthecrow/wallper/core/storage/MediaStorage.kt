package com.onthecrow.wallper.core.storage

import java.io.File

/**
 * Where wallpaper media lives. The wallpaper service runs in direct boot, so everything it reads
 * is kept in device-protected storage.
 *
 * The editor works on scratch files: a copy of the picked media, its thumbnail and what the export makes of
 * it. They are not in cacheDir, which the system empties whenever it needs space, also while they are in
 * use. Nothing clears them by itself: the editor clears them when an edit starts and when it ends. Their
 * names are fixed, so a process killed mid-edit leaves one set at most, gone with the next edit.
 */
interface MediaStorage {

    /** Scratch copy of the media picked in the editor. */
    val tempMediaFile: File

    /** Scratch thumbnail of the media picked in the editor. */
    val tempThumbnailFile: File

    /** Scratch output of the editor's export. */
    val tempProcessedFile: File

    /** Copies a content [uri] into [tempMediaFile]. Returns its path, or null if the uri can't be read. */
    fun copyToTemp(uri: String): String?

    /** Copies [tempMediaFile] to permanent storage and returns the new path. */
    fun persistTempMedia(): String

    /** Copies [tempProcessedFile] to permanent storage and returns the new path. */
    fun persistTempProcessed(): String

    /** Copies [tempThumbnailFile] to permanent storage. Returns the new path, or null if there is none. */
    fun persistTempThumbnail(): String?

    /** Deletes all the scratch files. */
    fun clearTemp()

    fun delete(path: String)

    /** The size of the file at [path] in bytes; 0 if there is none. Disk work: off the main thread. */
    fun sizeOf(path: String): Long
}
