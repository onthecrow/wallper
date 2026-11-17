package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.Tint
import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.editor.domain.SaveWallpaperUseCase
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia
import com.onthecrow.wallper.editor.export.ExportArea
import com.onthecrow.wallper.editor.export.VideoExporter
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.AddWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.UpdateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.model.NewWallpaper
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class SaveWallpaperUseCaseImpl @Inject constructor(
    private val mediaStorage: MediaStorage,
    private val videoExporter: VideoExporter,
    private val addWallpaperUseCase: AddWallpaperUseCase,
    private val updateWallpaperUseCase: UpdateWallpaperUseCase,
    private val activateWallpaperUseCase: ActivateWallpaperUseCase,
    private val appSettings: AppSettingsSource,
    private val dispatchersProvider: DispatchersProvider,
) : SaveWallpaperUseCase {

    override fun invoke(
        media: PreparedMedia,
        framing: Framing,
        clip: ClipRange,
        process: Boolean,
        tint: Tint?,
    ): Flow<ExportStatus> = flow {
        // The export works in scratch storage too: a failed or abandoned one leaves nothing elsewhere.
        val processed = mediaStorage.tempProcessedFile.takeIf { media.type == MediaType.VIDEO && process }
        val area = processed?.let { exportArea(framing, media) }
        val export = if (processed != null && area != null) {
            mediaStorage.delete(processed.path)
            videoExporter.export(media.path, processed.path, area.rect, clip, tint)
        } else {
            flowOf(ExportStatus.Success)
        }

        emitAll(
            export.onEach { status ->
                // Once the export is done the save runs to its end, even if the editor stops waiting for it
                // (its Cancel, just then): stopped halfway, it could leave a wallpaper whose files are gone.
                if (status is ExportStatus.Success) {
                    withContext(NonCancellable) { save(media, framing, clip, tint, area) }
                }
            }
        )
    }.flowOn(dispatchersProvider.io)

    /**
     * Copies the files the wallpaper keeps to permanent storage, only now that it is ready, and saves it. If
     * that fails halfway, the copies go again: no file outlives a wallpaper that wasn't saved.
     */
    private suspend fun save(media: PreparedMedia, framing: Framing, clip: ClipRange, tint: Tint?, export: ExportArea?) {
        val edited = media.edited
        val persisted = mutableListOf<String>()
        try {
            // An edited wallpaper keeps its thumbnail: it is the first frame, whatever the crop and trim.
            val thumbnailPath = edited?.thumbnailPath ?: persistThumbnail(media.type)?.also(persisted::add).orEmpty()
            val wallpaper = if (export != null) {
                // The export is all a processed video needs: it can't be edited again, so the original goes.
                // The tint is in its frames; the thumbnail, made before, is shown under it. Its windows are in
                // pixels of the export as asked for: an encoder that made it smaller kept its proportions.
                NewWallpaper.ProcessedVideo(
                    path = mediaStorage.persistTempProcessed().also(persisted::add),
                    thumbnailPath = thumbnailPath,
                    tint = tint,
                    frameWidth = export.width,
                    frameHeight = export.height,
                    crop = export.framing.crop,
                    turnedCrop = export.framing.turned,
                )
            } else {
                // An edited wallpaper's original is in permanent storage already.
                val originalPath = if (edited != null) media.path else mediaStorage.persistTempMedia().also(persisted::add)
                NewWallpaper.Media(
                    media.type, originalPath, thumbnailPath, framing.crop, clip, media.mediaInfo, tint, framing.turned,
                )
            }
            if (edited != null) updateWallpaperUseCase(edited.id, wallpaper) else add(wallpaper)
        } catch (error: Throwable) {
            persisted.forEach(mediaStorage::delete)
            throw error
        }
    }

    /**
     * Both ways the screen can be turned, where an encoder takes that size; the crop alone where none does, the
     * other way then derived inside it, smaller.
     */
    private fun exportArea(framing: Framing, media: PreparedMedia): ExportArea {
        val both = ExportArea.of(framing, media.mediaInfo.displayWidth, media.mediaInfo.displayHeight)
        return if (videoExporter.canEncode(both.width, both.height)) both else ExportArea.cropOnly(framing)
    }

    /** A new wallpaper becomes the active one right away if the user wants it so. */
    private suspend fun add(wallpaper: NewWallpaper) {
        val id = addWallpaperUseCase(wallpaper)
        if (appSettings.settings.value.activateNewWallpapers) activateWallpaperUseCase(id)
    }

    private fun persistThumbnail(type: MediaType): String? =
        if (type == MediaType.VIDEO) mediaStorage.persistTempThumbnail() else null
}
