package com.onthecrow.wallper.core.designsystem.component.trimbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

/**
 * Pictures for the strip of a [TrimBar]. `images[i]` stands for `[i × intervalMs, (i + 1) × intervalMs)`
 * of the video, best decoded from the middle of it. An image that is null or not in the list yet is drawn
 * as a placeholder, so the list may grow as frames are decoded.
 *
 * @param aspectRatio width / height of a frame. The strip is tiled with frames of this shape; an image of
 * another shape is cropped to it.
 */
@Immutable
class TrimBarFrames(
    val images: List<ImageBitmap?>,
    val intervalMs: Long,
    val aspectRatio: Float,
) {
    init {
        require(intervalMs > 0) { "intervalMs must be positive: $intervalMs" }
        require(aspectRatio > 0f) { "aspectRatio must be positive: $aspectRatio" }
    }

    companion object {
        /** No pictures: the strip is a placeholder. */
        val Empty = TrimBarFrames(emptyList(), intervalMs = 1_000, aspectRatio = 1f)
    }
}

/**
 * Colours of a [TrimBar]: the frame, its handles and the playhead are in one accent colour by default.
 * A data class, so equal colours from [TrimBarDefaults.colors] let the bar skip recomposition.
 */
@Immutable
data class TrimBarColors(
    /** The frame around the kept part, the ends that hold the handles included. */
    val track: Color,
    /** The bar in each end of the frame. */
    val handle: Color,
    val playhead: Color,
    /** The seconds and the dots between them. */
    val scale: Color,
    /** Where a frame isn't decoded yet. */
    val framePlaceholder: Color,
)

object TrimBarDefaults {

    /** How wide one second of video is on the strip. */
    val SecondWidth: Dp = 64.dp

    /** A handle closer than this to the playhead sticks to it. */
    val SnapDistance: Dp = 4.dp

    /** How far the frames beyond a handle fade out. */
    val FadeWidth: Dp = 32.dp

    /** The shortest part the handles keep. */
    const val MIN_DURATION_MS: Long = 1_000

    /** The height of a frame on the strip: decode frames about this tall. */
    val FrameHeight: Dp = 48.dp

    /**
     * How much video one frame of the strip spans when frames of [aspectRatio] (width / height) are laid
     * edge to edge at [secondWidth]. Decoding one frame per this interval fills the strip without repeats.
     */
    fun frameIntervalMs(aspectRatio: Float, secondWidth: Dp = SecondWidth): Long =
        (FrameHeight.value * aspectRatio / secondWidth.value * 1_000).roundToLong().coerceAtLeast(1)

    /** A moment written the way the scale writes it, `mm:ss`; for labels next to the bar. */
    fun formatTime(ms: Long): String = timeLabel(ms)

    @Composable
    fun colors(
        track: Color = MaterialTheme.colorScheme.primary,
        handle: Color = MaterialTheme.colorScheme.onPrimary,
        playhead: Color = MaterialTheme.colorScheme.primary,
        scale: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        framePlaceholder: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ): TrimBarColors = TrimBarColors(
        track = track,
        handle = handle,
        playhead = playhead,
        scale = scale,
        framePlaceholder = framePlaceholder,
    )
}
