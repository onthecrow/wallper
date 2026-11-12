package com.onthecrow.wallper.wallpapers.mapper

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.wallpapers.data.database.WallpaperEntity
import com.onthecrow.wallper.wallpapers.data.database.WallpaperGroupItemEntity
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper

/**
 * The rows as wallpapers, each group with its members in order ([groupItems] in the order of the query).
 * Members that aren't wallpapers of this version are left out of their groups.
 */
internal fun List<WallpaperEntity>.toDomain(groupItems: List<WallpaperGroupItemEntity>): List<Wallpaper> {
    val singles = mapNotNull { it.toDomain() }.associateBy { it.id }
    val membersByGroup = groupItems.groupBy({ it.groupId }, { it.wallpaperId })
    return mapNotNull { entity ->
        if (entity.isGroup) {
            entity.toGroup(membersByGroup[entity.id].orEmpty().mapNotNull(singles::get))
        } else {
            singles[entity.id]
        }
    }
}

/** A group (a slideshow) rather than one wallpaper. */
internal val WallpaperEntity.isGroup: Boolean get() = slideDurationMs != null

/** The group this row is, with its [members]. */
internal fun WallpaperEntity.toGroup(members: List<Wallpaper>) = Wallpaper.Group(
    id = id,
    members = members,
    slideDuration = SlideDuration(checkNotNull(slideDurationMs) { "Wallpaper $id isn't a group" }),
    isActive = isActive,
)

/**
 * One wallpaper. Null for a group, which needs its members ([toGroup]), a built-in this app version doesn't
 * know, or a row without media: such rows are skipped.
 */
internal fun WallpaperEntity.toDomain(): Wallpaper? = when {
    isGroup -> null
    builtIn != null -> BuiltInWallpaper.fromKey(builtIn)?.let { wallpaper ->
        Wallpaper.BuiltIn(id = id, wallpaper = wallpaper, isActive = isActive)
    }
    // Rows from before v4 keep the original too, but a processed video is final.
    processedPath != null -> Wallpaper.ProcessedVideo(
        id = id,
        path = processedPath,
        thumbnailPath = thumbnailPath,
        isActive = isActive,
    )
    originalPath != null -> Wallpaper.Media(
        id = id,
        type = type,
        originalPath = originalPath,
        thumbnailPath = thumbnailPath,
        crop = CropRect(cropLeft, cropTop, cropRight, cropBottom, cropRotation),
        clip = ClipRange(clipStartMs, clipEndMs),
        mediaInfo = MediaInfo(mediaWidth, mediaHeight, mediaRotation, mediaDurationMs),
        isActive = isActive,
    )
    else -> null
}

/** A new row, or with [id] the new version of one. */
internal fun NewWallpaper.toEntity(id: Int = 0, isActive: Boolean = false) = when (this) {
    is NewWallpaper.Media -> WallpaperEntity(
        id = id,
        type = type,
        originalPath = originalPath,
        processedPath = null,
        thumbnailPath = thumbnailPath,
        cropLeft = crop.left,
        cropTop = crop.top,
        cropRight = crop.right,
        cropBottom = crop.bottom,
        cropRotation = crop.rotation,
        clipStartMs = clip.startMs,
        clipEndMs = clip.endMs,
        mediaWidth = mediaInfo.width,
        mediaHeight = mediaInfo.height,
        mediaRotation = mediaInfo.rotation,
        mediaDurationMs = mediaInfo.durationMs,
        isActive = isActive,
    )
    is NewWallpaper.ProcessedVideo -> WallpaperEntity(
        id = id,
        type = MediaType.VIDEO,
        originalPath = null,
        processedPath = path,
        thumbnailPath = thumbnailPath,
        cropLeft = 0,
        cropTop = 0,
        cropRight = 0,
        cropBottom = 0,
        clipStartMs = 0,
        clipEndMs = 0,
        mediaWidth = 0,
        mediaHeight = 0,
        mediaRotation = 0,
        mediaDurationMs = 0,
        isActive = isActive,
    )
}

/** A group's wallpaper is a slideshow of its members; a group in a group (there is none) would be left out. */
internal fun Wallpaper.toSpec(): WallpaperSpec = when (this) {
    is Wallpaper.Media -> WallpaperSpec.Media(
        type = type,
        sourcePath = originalPath,
        thumbnailPath = thumbnailPath,
        crop = crop,
        clip = clip,
        mediaInfo = mediaInfo,
    )
    is Wallpaper.ProcessedVideo -> WallpaperSpec.ProcessedVideo(path = path, thumbnailPath = thumbnailPath)
    is Wallpaper.BuiltIn -> WallpaperSpec.BuiltIn(wallpaper)
    is Wallpaper.Group -> WallpaperSpec.Slideshow(
        slides = members.filterNot { it is Wallpaper.Group }.map { it.toSpec() },
        slideDuration = slideDuration,
    )
}

/** A group's row: what it shows is in its items; the media columns hold placeholders, like a built-in's. */
internal fun groupEntity(id: Int = 0, slideDuration: SlideDuration, isActive: Boolean = false) = WallpaperEntity(
    id = id,
    type = MediaType.VIDEO,
    originalPath = null,
    processedPath = null,
    thumbnailPath = "",
    cropLeft = 0,
    cropTop = 0,
    cropRight = 0,
    cropBottom = 0,
    clipStartMs = 0,
    clipEndMs = 0,
    mediaWidth = 0,
    mediaHeight = 0,
    mediaRotation = 0,
    mediaDurationMs = 0,
    isActive = isActive,
    slideDurationMs = slideDuration.ms,
)
