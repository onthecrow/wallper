package com.onthecrow.wallper.settings.storage

import com.onthecrow.wallper.core.mvi.State

internal data class StorageState(
    /** Largest first; null until counted. */
    val items: List<StorageItem>? = null,
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

    /** Only a video kept as it was can be optimised; a picture is always kept as it is. */
    val canOptimise: Boolean get() = kind == Kind.ORIGINAL_VIDEO
}
