package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.designsystem.component.colorpicker.HsvColor
import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBarDefaults.MIN_DURATION_MS
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.Tint
import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.domain.model.PreparedMedia

internal data class EditorState(
    /** A new wallpaper, not one being edited: what the bar says. */
    val isNew: Boolean = true,
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
    /** The colour the wallpaper is seen through, and whether it is. */
    val tint: TintChoice = TintChoice(),
    /** The tint's picker and switch are on screen, in place of the trim bar and the save row. */
    val isTintOpen: Boolean = false,
    /** The save under way, from its start until it fails; the editor closes once it succeeds. */
    val save: SaveRequest? = null,
    val exportStatus: ExportStatus? = null,
) : State {
    /** Whether the trim, the tint or the original / optimised choice differs from what the editor opened with. */
    val hasEdits: Boolean
        get() = media != null && (trim != media.initialTrim || isOptimised || tint.tint != media.edited?.tint)
}

/** A new wallpaper keeps the whole video until the user moves a handle; an edited one starts from its trim. */
internal val PreparedMedia.initialTrim: ClipRange
    get() = edited?.clip ?: ClipRange(startMs = 0, endMs = mediaInfo.durationMs)

/** A new wallpaper has no tint; an edited one starts from its own, the picker at its colour. */
internal val PreparedMedia.initialTint: TintChoice
    get() = edited?.tint?.let { TintChoice(isOn = true, color = HsvColor.fromArgb(it.argb)) } ?: TintChoice()

/**
 * The tint as the editor holds it: the picker keeps its [color] while the tint [isOn] or off, so switching
 * it off and on again brings the same colour back.
 */
internal data class TintChoice(
    val isOn: Boolean = false,
    val color: HsvColor = DEFAULT_COLOR,
) {
    /** What the wallpaper gets: nothing while it is off, or as good as off, fully transparent. */
    val tint: Tint? get() = if (isOn) Tint(color.toArgb()).orNull() else null

    companion object {
        /**
         * What the first switch gives: black at 30 %, the picture a little darker, so that icons and widgets
         * read better. Black has no hue: the picker's hue thumb starts at a blue, away from the area's thumb in
         * its corner, and that is the tone a drag up the area's right side gives first.
         */
        val DEFAULT_COLOR = HsvColor(hue = 210f, saturation = 0f, value = 0f, alpha = 0.3f)
    }
}

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
    val framing: Framing,
    val trim: ClipRange,
    val isOptimised: Boolean,
    val tint: Tint?,
)
