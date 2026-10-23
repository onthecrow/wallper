package com.onthecrow.wallper.editor.preview

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.scale
import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBarDefaults
import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBarFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Frames of the video at [path] for the trim bar, decoded one by one off the main thread: the strip fills in
 * as they arrive. The trim bar reads the state while drawing, so a new frame only redraws it.
 *
 * @param aspectRatio width / height of the video as shown.
 * @param paused holds the decoding, keeping the frames so far, while the export needs the decoders; it goes
 * on from there afterwards.
 */
@Composable
internal fun rememberVideoFrames(path: String, durationMs: Long, aspectRatio: Float, paused: Boolean): State<TrimBarFrames> {
    val heightPx = with(LocalDensity.current) { TrimBarDefaults.FrameHeight.roundToPx() }
    val intervalMs = stripFrameIntervalMs(durationMs, heightPx, aspectRatio)
    val frames = remember(path, intervalMs, aspectRatio) {
        mutableStateOf(TrimBarFrames(emptyList(), intervalMs, aspectRatio))
    }
    LaunchedEffect(frames, heightPx, paused) {
        if (paused) return@LaunchedEffect
        val count = ceilDiv(durationMs, intervalMs).toInt()
        // Fits either way round: the retriever keeps the shape and turns the frame upright.
        val boxPx = max(heightPx, (heightPx * aspectRatio).roundToInt())
        val images = ArrayList(frames.value.images)
        decodeFrames(path, from = images.size, count, intervalMs, boxPx).collect { image ->
            images += image
            frames.value = TrimBarFrames(images.toList(), intervalMs, aspectRatio)
        }
    }
    return frames
}

/**
 * How much video one frame of the strip stands for: one tile at the default scale, more when the frames
 * of the whole video, [heightPx] tall, wouldn't fit in [FRAME_PIXEL_BUDGET]; then each frame stretches
 * over a few tiles.
 */
internal fun stripFrameIntervalMs(durationMs: Long, heightPx: Int, aspectRatio: Float): Long {
    val maxFrames = max(1L, (FRAME_PIXEL_BUDGET / (heightPx.toFloat() * heightPx * aspectRatio)).toLong())
    return max(TrimBarDefaults.frameIntervalMs(aspectRatio), ceilDiv(durationMs, maxFrames))
}

/**
 * Frames [from] until [count], each from the middle of its [intervalMs], scaled to fit a [boxPx] square. A
 * frame that can't be read comes as null; a file that can't be read ends the flow.
 */
private fun decodeFrames(path: String, from: Int, count: Int, intervalMs: Long, boxPx: Int): Flow<ImageBitmap?> = flow {
    // MediaMetadataRetriever is AutoCloseable only since API 29, so no use {} here.
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        for (index in from until count) {
            val timeUs = (index * intervalMs + intervalMs / 2) * US_PER_MS
            emit(retriever.frameAt(timeUs, boxPx)?.asImageBitmap())
        }
    } finally {
        retriever.release()
    }
}
    .catch { error -> Timber.e(error, "Can't decode the trim bar frames of $path") }
    .flowOn(Dispatchers.IO)

/**
 * The key frame nearest to [timeUs], no bigger than [boxPx] either way. Close enough for a tile 48 dp tall,
 * and far cheaper than the exact frame, which is decoded from the key frame before it every time.
 */
private fun MediaMetadataRetriever.frameAt(timeUs: Long, boxPx: Int): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, boxPx, boxPx)
    } else {
        getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
            val factor = boxPx.toFloat() / max(frame.width, frame.height)
            frame.scale((frame.width * factor).roundToInt().coerceAtLeast(1), (frame.height * factor).roundToInt().coerceAtLeast(1))
                .also { if (it !== frame) frame.recycle() }
        }
    }

private fun ceilDiv(a: Long, b: Long): Long = (a + b - 1) / b

/** About 8 MB of frames: on a phone, a fresh frame for every tile of some 50 s of video, portrait or landscape. */
private const val FRAME_PIXEL_BUDGET = 2_000_000f
private const val US_PER_MS = 1_000L
