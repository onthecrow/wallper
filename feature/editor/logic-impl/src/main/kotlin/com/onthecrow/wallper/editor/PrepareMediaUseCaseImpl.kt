package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.editor.media.MediaInspector
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal class PrepareMediaUseCaseImpl @Inject constructor(
    private val mediaInspector: MediaInspector,
    private val mediaStorage: MediaStorage,
    private val dispatchersProvider: DispatchersProvider,
) : PrepareMediaUseCase {

    override suspend fun invoke(uri: String): Result<PreparedMedia> = withContext(dispatchersProvider.io) {
        runCatching {
            val type = if (mediaInspector.isVideo(uri)) MediaType.VIDEO else MediaType.IMAGE
            if (type == MediaType.VIDEO) {
                mediaInspector.writeVideoThumbnail(uri, mediaStorage.tempThumbnailFile)
            }
            val path = checkNotNull(mediaStorage.copyToTemp(uri)) { "Can't read $uri" }
            PreparedMedia(path = path, type = type, mediaInfo = mediaInspector.readMediaInfo(path, type))
        }.onFailure(Timber::e)
    }
}
