package com.onthecrow.wallper.core.model

/** Facts about a media file that rendering and cropping depend on. Rotation is in degrees. */
data class MediaInfo(
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationMs: Long,
)
