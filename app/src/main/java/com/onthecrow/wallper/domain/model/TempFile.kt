package com.onthecrow.wallper.domain.model

data class TempFile(
    val originalFilePath: String,
    val isVideo: Boolean = false,
    val thumbnailPath: String = "",
    val error: Throwable? = null
)