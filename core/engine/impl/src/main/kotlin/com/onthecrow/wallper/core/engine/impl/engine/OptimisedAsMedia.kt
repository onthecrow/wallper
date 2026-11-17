package com.onthecrow.wallper.core.engine.impl.engine

import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/**
 * An optimised video as GL takes one: its export is the frame, and its windows in it are the crops, one for
 * each way the screen can be turned; played from end to end, without its tint (the export put that into the
 * frames already), never panned: an export of a turned crop has corners outside the video. An export of unknown size is a frame of one by one cropped to all of it: the whole frame
 * over the whole screen, whatever its proportions.
 */
internal fun WallpaperSpec.ProcessedVideo.asMedia(): WallpaperSpec.Media {
    val isKnown = frameWidth > 0 && frameHeight > 0
    val width = if (isKnown) frameWidth else 1
    val height = if (isKnown) frameHeight else 1
    return WallpaperSpec.Media(
        type = MediaType.VIDEO,
        sourcePath = path,
        thumbnailPath = thumbnailPath,
        crop = crop?.takeIf { isKnown } ?: CropRect(left = 0, top = 0, right = width, bottom = height),
        turnedCrop = turnedCrop?.takeIf { isKnown },
        clip = ClipRange(startMs = 0, endMs = 0),
        mediaInfo = MediaInfo(width = width, height = height, rotation = 0, durationMs = 0),
        pansWithPages = false,
    )
}
