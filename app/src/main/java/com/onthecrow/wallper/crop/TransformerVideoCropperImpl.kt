@file:kotlin.OptIn(ExperimentalCoroutinesApi::class)

package com.onthecrow.wallper.crop

import android.content.Context
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.onthecrow.wallper.util.extractFloatMetadataUnsafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TransformerVideoCropperImpl(
    private val applicationContext: Context,
) : VideoCropper() {

    @OptIn(UnstableApi::class)
    override fun cropInternal(
        rectToCropFor: Rect,
        inputFilePath: String,
        outputFilePath: String
    ): Flow<VideoCroppingStatus> {
        return callbackFlow {
            var progressJob: Job? = null
            val transformer = getTransformer(
                onCompleted = {
                    progressJob?.cancel()
                    trySend(VideoCroppingStatus.Success)
                },
                onError = { error ->
                    progressJob?.cancel()
                    trySend(VideoCroppingStatus.Error(error))
                }
            )
            transformer.start(
                getEditedMediaItemForVideo(inputFilePath, rectToCropFor),
                outputFilePath
            )
            progressJob = getProgressRenewalJob(
                transformer,
                onProgressUpdated = { progress -> trySend(VideoCroppingStatus.InProgress(progress)) },
            )
            trySend(VideoCroppingStatus.InProgress())
            awaitClose {
                progressJob.cancel()
                transformer.cancel()
            }
        }.map { status ->
            if (status is VideoCroppingStatus.Error) {
                fallbackCropper?.crop(rectToCropFor, inputFilePath, outputFilePath)
            } else {
                null
            } ?: flowOf(status)
        }.flattenConcat()
    }

    @OptIn(UnstableApi::class)
    private fun getProgressRenewalJob(
        transformer: Transformer,
        onProgressUpdated: (Int) -> Unit
    ): Job {
        // TODO get rid of MainScope()
        return MainScope().launch(Dispatchers.Main) {
            val progressHolder = ProgressHolder()
            while (true) {
                delay(DELAY_PROGRESS_RENEWAL)
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgressUpdated(progressHolder.progress)
                }
            }
        }
    }

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
    private fun getVideoEffectsForVideo(
        metadata: VideoMetadata,
        ndcCoords: NDCCoords
    ): List<MatrixTransformation> {
        return listOf(
            ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(metadata.rotation)
                .build(),
            Crop(ndcCoords.left, ndcCoords.right, ndcCoords.bottom, ndcCoords.top),
        )
    }

    @OptIn(UnstableApi::class)
    private fun getEditedMediaItemForVideo(
        inputFilePath: String,
        rectToCropFor: Rect,
    ): EditedMediaItem {
        val videoMetadata = getVideoMetadata(inputFilePath)
        return EditedMediaItem.Builder(MediaItem.fromUri(inputFilePath))
            .setRemoveAudio(true)
            // TODO implement other frame rates later
            .setFrameRate(24)
            .setEffects(
                Effects(
                    listOf(),
                    getVideoEffectsForVideo(
                        videoMetadata,
                        NDCCoords.fromRect(rectToCropFor, videoMetadata.width, videoMetadata.height),
                    )
                )
            )
            .build()
    }

    private fun getVideoMetadata(inputFilePath: String): VideoMetadata {
        return MediaMetadataRetriever().run {
            setDataSource(inputFilePath)
            VideoMetadata(
                extractFloatMetadataUnsafe(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                extractFloatMetadataUnsafe(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                extractFloatMetadataUnsafe(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
            ).also { release() }
        }
    }

    private class VideoMetadata(val width: Float, val height: Float, val rotation: Float)

    companion object {
        private const val DELAY_PROGRESS_RENEWAL = 500L
    }
}