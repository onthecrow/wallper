package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBarDefaults.MIN_DURATION_MS
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia

internal data class EditorState(
    /** Null until the picked file is copied and inspected, or the saved wallpaper is read. */
    val media: PreparedMedia? = null,
    /** The part of the video the wallpaper keeps: all of it once it is prepared, `0..0` if its length is unknown. */
    val trim: ClipRange = ClipRange(startMs = 0, endMs = 0),
    /**
     * Whether the user wants the preview to play: not until they press play, and not after the kept part
     * has played to its end. Dragging the trim bar pauses it without changing this.
     */
    val isPlaying: Boolean = false,
    /**
     * Optimised: only a cropped and trimmed export of the video is kept, which can't be edited again.
     * Original, the default: the video is kept as it is and cropped while it plays.
     */
    val isOptimised: Boolean = false,
    /** Whether the explanation of original and optimised was ever shown, in this editor or an earlier one. */
    val isProcessingInfoSeen: Boolean = false,
    /** The explanation on screen, null when it isn't. */
    val processingInfo: ProcessingInfo? = null,
    /** The save under way, from its start until it fails; the editor closes once it succeeds. */
    val save: SaveRequest? = null,
    val exportStatus: ExportStatus? = null,
) : State

/**
 * The trim with its start moved in to [positionMs]; null unless the playhead is inside the kept part, past its
 * start, and leaves it as long as the trim bar allows. The buttons only narrow the kept part: the handles widen it.
 */
internal fun ClipRange.withStartAt(positionMs: Long): ClipRange? =
    copy(startMs = positionMs).takeIf { positionMs > startMs && it.isLongEnough }

/** The trim with its end moved in to [positionMs]; the same rule as [withStartAt], before its end. */
internal fun ClipRange.withEndAt(positionMs: Long): ClipRange? =
    copy(endMs = positionMs).takeIf { positionMs < endMs && it.isLongEnough }

private val ClipRange.isLongEnough get() = endMs - startMs >= MIN_DURATION_MS

/**
 * The explanation of original and optimised. With [optimisesOnConfirm] it came on the first switch to
 * optimised, which waits for its OK.
 */
internal data class ProcessingInfo(val optimisesOnConfirm: Boolean = false)

internal data class SaveRequest(
    val media: PreparedMedia,
    val crop: CropRect,
    val trim: ClipRange,
    val isOptimised: Boolean,
)
