package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.editor.domain.IsMediaSupportedUseCase
import com.onthecrow.wallper.editor.media.MediaInspector
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class IsMediaSupportedUseCaseImpl @Inject constructor(
    private val mediaInspector: MediaInspector,
    private val dispatchersProvider: DispatchersProvider,
) : IsMediaSupportedUseCase {
    override suspend fun invoke(uri: String): Boolean = withContext(dispatchersProvider.io) {
        mediaInspector.isVideo(uri) || mediaInspector.isPicture(uri)
    }
}
