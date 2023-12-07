package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.StorageRepository
import javax.inject.Inject

class FileValidationUseCase @Inject constructor(
    private val storageRepository: StorageRepository
) {
    operator fun invoke(uri: String): Boolean {
        return storageRepository.isFileVideo(uri) || storageRepository.isFilePicture(uri)
    }
}