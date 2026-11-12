package com.onthecrow.wallper.wallpapers.model

import com.onthecrow.wallper.core.model.BuiltInWallpaper

internal data class WallpaperItem(
    val id: Int,
    /** Null while there is nothing to show: a built-in's preview or a series' picture is still being made. */
    val picturePath: String?,
    val isActive: Boolean,
    /** Set for the wallpapers that ship with the app: they have settings instead of media, and stay. */
    val builtIn: BuiltInWallpaper? = null,
    /** The user's video kept only as an optimised export: it can't be edited, only deleted. */
    val isOptimised: Boolean = false,
    /** A group (a series): its picture is made of its [cover]'s. */
    val isGroup: Boolean = false,
    /** A group's first members, whose pictures make its picture (`MakeSeriesPictureUseCase`). */
    val cover: List<WallpaperItem> = emptyList(),
) {
    /** The built-ins whose rendered previews this item's picture shows. */
    val previewsShown: List<BuiltInWallpaper> get() = listOfNotNull(builtIn) + cover.mapNotNull { it.builtIn }

    /** Its picture is made in the background: until it is there, the card shows the placeholder's colour. */
    val isPictureMade: Boolean get() = builtIn != null || isGroup
}

/**
 * A built-in's picture is its preview once it is rendered ([previews]: null for one this device can't render),
 * and so it is in a group's cover. A group's picture is the one made of its cover, from [seriesPictures].
 */
internal fun List<WallpaperItem>.withPreviews(
    previews: Map<BuiltInWallpaper, String?>,
    seriesPictures: Map<Int, String> = emptyMap(),
): List<WallpaperItem> = map { item ->
    when {
        item.builtIn != null -> item.copy(picturePath = previews[item.builtIn])
        item.isGroup -> item.copy(picturePath = seriesPictures[item.id], cover = item.cover.withPreviews(previews))
        else -> item
    }
}
