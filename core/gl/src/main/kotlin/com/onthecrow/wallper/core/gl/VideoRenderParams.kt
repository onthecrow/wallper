package com.onthecrow.wallper.core.gl

import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.Tint

/**
 * The surface's size in pixels when the renderer is made (it follows the surface from then on), the crops of
 * the video for each way the screen can be turned, and the tint to lay over it, if any.
 */
data class VideoRenderParams(
    val width: Int,
    val height: Int,
    val framing: Framing,
    val mediaInfo: MediaInfo,
    val tint: Tint? = null,
)
