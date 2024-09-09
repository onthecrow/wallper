package com.onthecrow.wallper.domain

import android.graphics.Rect
import com.onthecrow.wallper.crop.FallbackVideoCropper
import com.onthecrow.wallper.crop.MainVideoCropper
import com.onthecrow.wallper.crop.VideoCropper
import com.onthecrow.wallper.crop.VideoCroppingStatus
import com.onthecrow.wallper.data.StorageRepository
import com.onthecrow.wallper.data.WallpapersRepository
import com.onthecrow.wallper.domain.model.TempFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject


class CreateWallpaperUseCase @Inject constructor(
    @MainVideoCropper private val mainVideoCropper: VideoCropper,
    @FallbackVideoCropper private val fallbackVideoCropper: VideoCropper,
    private val storageRepository: StorageRepository,
    private val wallpapersRepository: WallpapersRepository,
) {
    suspend operator fun invoke(
        rect: Rect,
        tempFile: TempFile,
        additionalProcessing: Boolean,
    ): Flow<VideoCroppingStatus> {
        val filePath = storageRepository.saveTempFile()
        val thumbnailPath = storageRepository.saveTempThumbnail()
        val croppedFilePath = filePath + SUFFIX_CROPPED_FILE

        return when {
            tempFile.isVideo && additionalProcessing ->
                mainVideoCropper.setFallbackCropper(fallbackVideoCropper)
                    .crop(rect, filePath ?: "", croppedFilePath)

            else -> flowOf(VideoCroppingStatus.Success)
        }.onEach { status ->
            if (status is VideoCroppingStatus.Success) {
                wallpapersRepository.saveWallpaper(
                    originalFileUri = filePath ?: "",
                    croppedFilePath = if (additionalProcessing) croppedFilePath else "",
                    thumbnailUri = thumbnailPath ?: "",
                    rect = rect,
                    isVideo = tempFile.isVideo,
                    isProcessed = additionalProcessing,
                )
            }
        }
    }

    companion object {
        private const val SUFFIX_CROPPED_FILE = "_cropped.mp4"
    }
}
