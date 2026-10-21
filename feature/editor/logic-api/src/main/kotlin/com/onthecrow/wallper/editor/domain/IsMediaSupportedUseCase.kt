package com.onthecrow.wallper.editor.domain

/** True if [uri] points to a picture or a video the editor can open. */
interface IsMediaSupportedUseCase {
    suspend operator fun invoke(uri: String): Boolean
}
