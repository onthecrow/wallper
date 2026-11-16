package com.onthecrow.wallper.settings.storage

import com.onthecrow.wallper.core.mvi.State

internal data class StorageState(
    /** Largest first; null until counted. */
    val items: List<StorageItem>? = null,
    /** The wallpaper whose deletion waits for the user's confirmation. */
    val deleteCandidate: Int? = null,
) : State {
    val totalBytes: Long? get() = items?.sumOf { it.bytes }
}

/** One of the user's wallpapers and what it takes. */
internal data class StorageItem(
    val id: Int,
    val picturePath: String,
    val kind: Kind,
    val bytes: Long,
) {
    enum class Kind { ORIGINAL_VIDEO, OPTIMISED_VIDEO, PICTURE }

    /** As on the list's card: an optimised video keeps no original to edit, it can only be deleted. */
    val canEdit: Boolean get() = kind != Kind.OPTIMISED_VIDEO
}
