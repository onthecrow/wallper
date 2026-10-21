package com.onthecrow.wallper.editor.domain.model

/** The video encoder failed; [errorCode] is the encoder's own code, shown to the user for bug reports. */
class VideoExportException(
    val errorCode: Int,
    cause: Throwable,
) : Exception(cause.message, cause)
