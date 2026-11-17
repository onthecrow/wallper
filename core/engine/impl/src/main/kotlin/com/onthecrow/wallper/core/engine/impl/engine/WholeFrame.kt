package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/**
 * An optimised video as GL takes one: its whole frame, a frame of one by one cropped to all of it, over the
 * whole screen, never panned, played from end to end. Without its tint: the export put that into the frames
 * already. A series on one GL thread and the series' preview draw it so.
 */
internal fun WallpaperSpec.ProcessedVideo.asWholeFrame() = WallpaperSpec.Media(
    type = MediaType.VIDEO,
    sourcePath = path,
    thumbnailPath = thumbnailPath,
    crop = CropRect(left = 0, top = 0, right = 1, bottom = 1),
    clip = ClipRange(startMs = 0, endMs = 0),
    mediaInfo = MediaInfo(width = 1, height = 1, rotation = 0, durationMs = 0),
)
