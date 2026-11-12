package com.onthecrow.wallper.wallpapers.model

import com.onthecrow.wallper.core.model.BuiltInWallpaper

internal data class WallpaperItem(
    val id: Int,
    /** Null while there is nothing to show: a built-in's preview is still being rendered. */
    val picturePath: String?,
    val isActive: Boolean,
    /** Set for the wallpapers that ship with the app: they have settings instead of media, and stay. */
    val builtIn: BuiltInWallpaper? = null,
    /** The user's video kept only as an optimised export: it can't be edited, only deleted. */
    val isOptimised: Boolean = false,
    /** A group (a series): its picture is its first member's. */
    val isGroup: Boolean = false,
    /** A group's first member when that is a built-in: the group's picture is its preview. */
    val coverBuiltIn: BuiltInWallpaper? = null,
) {
    /** The built-in whose rendered preview is this item's picture, if any. */
    val previewOf: BuiltInWallpaper? get() = builtIn ?: coverBuiltIn
}

/** A built-in's picture, and a group's that starts with one, is its preview once it is rendered. */
internal fun List<WallpaperItem>.withPreviews(previews: Map<BuiltInWallpaper, String>) = map { item ->
    item.previewOf?.let(previews::get)?.let { item.copy(picturePath = it) } ?: item
}
