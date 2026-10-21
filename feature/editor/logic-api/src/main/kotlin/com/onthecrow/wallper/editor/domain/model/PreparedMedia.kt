package com.onthecrow.wallper.editor.domain.model

import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType

/** The picked media copied into scratch storage, ready to be cropped. */
data class PreparedMedia(
    val path: String,
    val type: MediaType,
    val mediaInfo: MediaInfo,
)
