package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.StorageRepository
import com.onthecrow.wallper.domain.model.TempFile
import javax.inject.Inject

class PrepareFileUseCase @Inject constructor(
    private val storageRepository: StorageRepository,
) {
    operator fun invoke(uri: String): TempFile {
        return if (storageRepository.isFileVideo(uri)) {
            prepareVideo(uri)
        } else {
            preparePhoto(uri)
        }
    }

    private fun prepareVideo(uri: String): TempFile {
        val thumbnail = storageRepository.makeThumbnailTemp(uri)
        return TempFile(
            originalFilePath = storageRepository.copyFileTemp(uri),
            thumbnailPath = thumbnail.first ?: "",
            isVideo = true,
            error = thumbnail.second
        )
    }

    private fun preparePhoto(uri: String) = TempFile(
        originalFilePath = storageRepository.copyFileTemp(uri)
    )
}
