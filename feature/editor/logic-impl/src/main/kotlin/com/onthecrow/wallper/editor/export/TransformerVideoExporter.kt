package com.onthecrow.wallper.editor.export

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.VideoExportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Media3 Transformer export. Transformer must be driven from the main looper, hence `flowOn(main)`. */
internal class TransformerVideoExporter @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val dispatchersProvider: DispatchersProvider,
) : VideoExporter {

    @OptIn(UnstableApi::class)
    override fun export(
        inputPath: String,
        outputPath: String,
        crop: CropRect,
        clip: ClipRange,
    ): Flow<ExportStatus> = callbackFlow {
        val transformer = getTransformer(
            onCompleted = {
                trySend(ExportStatus.Success)
                close()
            },
            onError = { error ->
                trySend(ExportStatus.Error(VideoExportException(error.errorCode, error)))
                close()
            }
        )
        transformer.start(getEditedMediaItemForVideo(inputPath, crop, clip), outputPath)
        trySend(ExportStatus.InProgress())
        val progressJob = launch {
            val progressHolder = ProgressHolder()
            while (isActive) {
                delay(DELAY_PROGRESS_RENEWAL)
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(ExportStatus.InProgress(progressHolder.progress))
                }
            }
        }
        awaitClose {
            progressJob.cancel()
            transformer.cancel()
        }
    }.flowOn(dispatchersProvider.main)

    @OptIn(UnstableApi::class)
    private fun getTransformer(
        onCompleted: () -> Unit,
        onError: (ExportException) -> Unit,
    ): Transformer {
        return Transformer.Builder(applicationContext)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    exportResult: ExportResult
                ) {
                    super.onCompleted(composition, exportResult)
                    onCompleted()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    super.onError(composition, exportResult, exportException)
                    onError(exportException)
                }
            })
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun getVideoEffectsForVideo(ndcCoords: NDCCoords): List<MatrixTransformation> {
        // Transformer's decoder already turns frames upright, so the crop applies to the frame as shown.
        return listOf(Crop(ndcCoords.left, ndcCoords.right, ndcCoords.bottom, ndcCoords.top))
    }

    @OptIn(UnstableApi::class)
    private fun getEditedMediaItemForVideo(
        inputPath: String,
        crop: CropRect,
        clip: ClipRange,
    ): EditedMediaItem {
        val videoMetadata = getVideoMetadata(inputPath)
        return EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(inputPath)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build()
                )
                .build()
        )
            .setRemoveAudio(true)
            // TODO implement other frame rates later
            .setEffects(
                Effects(
                    listOf(),
                    getVideoEffectsForVideo(
                        NDCCoords.fromRect(crop, videoMetadata.displayWidth, videoMetadata.displayHeight),
                    )
                )
            )
            .build()
    }

    private fun getVideoMetadata(inputPath: String): VideoMetadata {
        return MediaMetadataRetriever().run {
            setDataSource(inputPath)
            VideoMetadata(
                extractFloatMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                extractFloatMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                extractFloatMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
            ).also { release() }
        }
    }

    private fun MediaMetadataRetriever.extractFloatMetadata(metadataKey: Int): Float =
        extractMetadata(metadataKey)?.toFloat() ?: throw IllegalStateException("Can't retrieve metadata!")

    private class VideoMetadata(val width: Float, val height: Float, val rotation: Float) {
        val displayWidth: Float get() = if (rotation % 180f == 0f) width else height
        val displayHeight: Float get() = if (rotation % 180f == 0f) height else width
    }

    companion object {
        private const val DELAY_PROGRESS_RENEWAL = 500L
    }
}
