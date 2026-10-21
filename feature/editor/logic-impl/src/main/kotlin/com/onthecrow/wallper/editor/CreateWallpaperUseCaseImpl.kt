package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.editor.domain.CreateWallpaperUseCase
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.editor.export.VideoExporter
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

internal class CreateWallpaperUseCaseImpl @Inject constructor(
    private val mediaStorage: MediaStorage,
    private val videoExporter: VideoExporter,
    private val saveWallpaperUseCase: SaveWallpaperUseCase,
    private val dispatchersProvider: DispatchersProvider,
) : CreateWallpaperUseCase {

    override fun invoke(
        media: PreparedMedia,
        crop: CropRect,
        clip: ClipRange,
        preCrop: Boolean,
    ): Flow<ExportStatus> = flow {
        val isVideo = media.type == MediaType.VIDEO
        val originalPath = mediaStorage.persistTempMedia()
        val thumbnailPath = if (isVideo) mediaStorage.persistTempThumbnail() else null
        val processedPath = (originalPath + SUFFIX_CROPPED_FILE).takeIf { isVideo && preCrop }

        val export = if (processedPath != null) {
            mediaStorage.delete(processedPath)
            videoExporter.export(originalPath, processedPath, crop, clip)
        } else {
            flowOf(ExportStatus.Success)
        }

        emitAll(
            export.onEach { status ->
                if (status is ExportStatus.Success) {
                    saveWallpaperUseCase(
                        NewWallpaper(
                            type = media.type,
                            originalPath = originalPath,
                            processedPath = processedPath,
                            thumbnailPath = thumbnailPath.orEmpty(),
                            crop = crop,
                            clip = clip,
                            mediaInfo = media.mediaInfo,
                        )
                    )
                }
            }
        )
    }.flowOn(dispatchersProvider.io)

    companion object {
        private const val SUFFIX_CROPPED_FILE = "_cropped.mp4"
    }
}
