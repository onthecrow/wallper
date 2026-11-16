package com.onthecrow.wallper.editor.export

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Size
import androidx.media3.effect.GlMatrixTransformation
import androidx.media3.effect.RgbMatrix
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Tint
import com.onthecrow.wallper.editor.domain.model.ExportOutput
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
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

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
        tint: Tint?,
    ): Flow<ExportStatus> = callbackFlow {
        // Disk work: off the main thread, which only drives the Transformer.
        val metadata = withContext(dispatchersProvider.io) {
            // An edit of a saved wallpaper copies nothing into scratch storage, so its directory may be missing.
            File(outputPath).parentFile?.mkdirs()
            getVideoMetadata(inputPath)
        }
        val item = getEditedMediaItemForVideo(inputPath, crop, clip, tint, metadata)
        // What it makes: the crop as it is shown, until an encoder that can't take it says otherwise.
        var output = ExportOutput(
            width = crop.width,
            height = crop.height,
            durationMs = ((clip.endMsOrNull ?: metadata.durationMs) - clip.startMs).coerceAtLeast(0),
            codec = codecName(VIDEO_MIME_TYPE),
        )
        val startedAt = SystemClock.elapsedRealtime()
        val transformer = getTransformer(
            onCompleted = {
                trySend(ExportStatus.Success)
                close()
            },
            onError = { error ->
                trySend(ExportStatus.Error(VideoExportException(error.errorCode, error)))
                close()
            },
            onFallback = { fallback ->
                Timber.w(
                    "The encoder can't make %s: Transformer falls back to a height of %d, %s",
                    output, fallback.outputHeight, fallback.videoMimeType,
                )
                output = output.after(fallback)
            },
        )
        transformer.start(item, outputPath)
        trySend(ExportStatus.InProgress(output = output))
        val progressJob = launch {
            val progressHolder = ProgressHolder()
            val pace = ExportPace()
            while (isActive) {
                delay(DELAY_PROGRESS_RENEWAL)
                if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val progress = progressHolder.progress
                    val remainingMs = pace.remainingMs(progress, SystemClock.elapsedRealtime() - startedAt)
                    trySend(ExportStatus.InProgress(progress, remainingMs, output))
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
        onFallback: (TransformationRequest) -> Unit,
    ): Transformer {
        return Transformer.Builder(applicationContext)
            .setVideoMimeType(VIDEO_MIME_TYPE)
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

                override fun onFallbackApplied(
                    composition: Composition,
                    originalTransformationRequest: TransformationRequest,
                    fallbackTransformationRequest: TransformationRequest,
                ) {
                    onFallback(fallbackTransformationRequest)
                }
            })
            .build()
    }

    /**
     * Cuts [crop] out of frames and turns it upright. Transformer's decoder already turns frames upright, so
     * the crop applies to the frame as shown, [frameWidth] x [frameHeight] in the media's metadata.
     */
    @OptIn(UnstableApi::class)
    private class CropTransformation(
        private val crop: CropRect,
        private val frameWidth: Float,
        private val frameHeight: Float,
    ) : GlMatrixTransformation {
        private val matrix = CropMatrix(crop, frameWidth, frameHeight).toGlArray()

        override fun configure(inputWidth: Int, inputHeight: Int) = Size(
            (crop.width * inputWidth / frameWidth).roundToInt(),
            (crop.height * inputHeight / frameHeight).roundToInt(),
        )

        override fun getGlMatrixArray(presentationTimeUs: Long): FloatArray = matrix
    }

    /**
     * Puts [tint] into every frame. An SDR video is processed in its own gamma values, so the frames come
     * out as the wallpaper's renderers and the editor's preview show the tint. Media3 folds it into the
     * crop's shader: no pass of its own.
     */
    @OptIn(UnstableApi::class)
    private class TintEffect(tint: Tint) : RgbMatrix {
        private val matrix = TintMatrix(tint).toGlArray()

        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = matrix
    }

    @OptIn(UnstableApi::class)
    private fun getEditedMediaItemForVideo(
        inputPath: String,
        crop: CropRect,
        clip: ClipRange,
        tint: Tint?,
        videoMetadata: VideoMetadata,
    ): EditedMediaItem {
        return EditedMediaItem.Builder(
            MediaItem.Builder()
                .setUri(inputPath)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .apply { clip.endMsOrNull?.let { setEndPositionMs(it) } }
                        .build()
                )
                .build()
        )
            .setRemoveAudio(true)
            // TODO implement other frame rates later
            .setEffects(
                Effects(
                    listOf(),
                    listOfNotNull(
                        CropTransformation(crop, videoMetadata.displayWidth, videoMetadata.displayHeight),
                        tint?.let(::TintEffect),
                    ),
                )
            )
            .build()
    }

    private fun getVideoMetadata(inputPath: String): VideoMetadata {
        // MediaMetadataRetriever is AutoCloseable only since API 29, so no use {} here.
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(inputPath)
            return VideoMetadata(
                retriever.extractFloatMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                retriever.extractFloatMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                retriever.extractFloatMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
            )
        } finally {
            retriever.release()
        }
    }

    private fun MediaMetadataRetriever.extractFloatMetadata(metadataKey: Int): Float =
        extractMetadata(metadataKey)?.toFloat() ?: throw IllegalStateException("Can't retrieve metadata!")

    private class VideoMetadata(val width: Float, val height: Float, val rotation: Float, val durationMs: Long) {
        val displayWidth: Float get() = if (rotation % 180f == 0f) width else height
        val displayHeight: Float get() = if (rotation % 180f == 0f) height else width
    }

    /** [fallback] applied to what the export makes: another size (its height as shown), another codec. */
    @OptIn(UnstableApi::class)
    private fun ExportOutput.after(fallback: TransformationRequest): ExportOutput {
        val resized = if (fallback.outputHeight == C.LENGTH_UNSET) this else withHeight(fallback.outputHeight)
        return fallback.videoMimeType?.let { resized.copy(codec = codecName(it)) } ?: resized
    }

    /** The codec's name as people know it. */
    @OptIn(UnstableApi::class)
    private fun codecName(mimeType: String): String = when (mimeType) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "H.265"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        else -> mimeType.substringAfter('/').uppercase()
    }

    companion object {
        private const val DELAY_PROGRESS_RENEWAL = 500L
        private const val VIDEO_MIME_TYPE = MimeTypes.VIDEO_H264
    }
}
