package com.onthecrow.wallper.editor.domain

/**
 * Deletes what [PrepareMediaUseCase] and the export left in scratch storage, for when the editor closes.
 * Quick and blocking: it only deletes a few files.
 */
fun interface DiscardPreparedMediaUseCase {
    operator fun invoke()
}
