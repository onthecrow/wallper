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
        // The export works in scratch storage too: a failed or abandoned one leaves nothing elsewhere.
        val processed = mediaStorage.tempProcessedFile.takeIf { media.type == MediaType.VIDEO && preCrop }
        val export = if (processed != null) {
            mediaStorage.delete(processed.path)
            videoExporter.export(media.path, processed.path, crop, clip)
        } else {
            flowOf(ExportStatus.Success)
        }

        emitAll(
            export.onEach { status ->
                if (status is ExportStatus.Success) save(media, crop, clip, isProcessed = processed != null)
            }
        )
    }.flowOn(dispatchersProvider.io)

    /**
     * Copies the scratch files to permanent storage, only now that the wallpaper is ready, and saves it. If
     * that fails halfway, the copies go again: no file outlives a wallpaper that wasn't saved.
     */
    private suspend fun save(media: PreparedMedia, crop: CropRect, clip: ClipRange, isProcessed: Boolean) {
        val persisted = mutableListOf<String>()
        try {
            val originalPath = mediaStorage.persistTempMedia().also(persisted::add)
            val processedPath = if (isProcessed) mediaStorage.persistTempProcessed().also(persisted::add) else null
            val thumbnailPath = if (media.type == MediaType.VIDEO) {
                mediaStorage.persistTempThumbnail()?.also(persisted::add)
            } else {
                null
            }
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
        } catch (error: Throwable) {
            persisted.forEach(mediaStorage::delete)
            throw error
        }
    }
}
