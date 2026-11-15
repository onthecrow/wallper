package com.onthecrow.wallper.editor.domain.model

import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.Tint

/** The media the editor works on: picked media copied into scratch storage, or a saved wallpaper's original. */
data class PreparedMedia(
    val path: String,
    val type: MediaType,
    val mediaInfo: MediaInfo,
    /** Null for picked media, which becomes a new wallpaper. */
    val edited: EditedWallpaper? = null,
)

/** The saved wallpaper being edited: the editor starts from its crop, trim and tint, and saving replaces it. */
data class EditedWallpaper(
    val id: Int,
    val thumbnailPath: String,
    val crop: CropRect,
    val clip: ClipRange,
    val tint: Tint? = null,
)
