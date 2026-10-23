package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.editor.domain.DiscardPreparedMediaUseCase
import javax.inject.Inject

internal class DiscardPreparedMediaUseCaseImpl @Inject constructor(
    private val mediaStorage: MediaStorage,
) : DiscardPreparedMediaUseCase {
    override fun invoke() = mediaStorage.clearTemp()
}
