package com.onthecrow.wallper.editor.crop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.reshaped
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.ln

/**
 * The crops of both ways the screen can be turned, each under a frame of its own: [base] for the screen as it
 * is now, of [screenAspectRatio], and [turned] for the screen turned a quarter. [isTurned] says which one the
 * editor shows; [turn] switches.
 *
 * A crop is the user's own once they move its picture, or if it was saved; until then it is derived from the
 * other one ([Framing]) and follows it: each turn to it puts it where the other one is now. The crop saved
 * first ([Framing.crop]) stays the first: an edit opened with the screen turned shows the other one first.
 *
 * @param start the crops the editor opened with, null for a new wallpaper.
 */
@Stable
internal class FramingState(
    private val mediaSize: IntSize,
    val screenAspectRatio: Float,
    start: Framing?,
    restored: Restored? = null,
) {
    /** Whether [Framing.crop] is the base one; a new wallpaper's is. */
    private val baseIsFirst = start == null || start.crop.isPortrait == (screenAspectRatio < 1f)

    private val startOwnBase = start == null || baseIsFirst || start.turned != null
    private val startOwnTurned = start != null && (!baseIsFirst || start.turned != null)

    val base: CropState
    val turned: CropState

    init {
        val baseStart = when {
            start == null -> null
            baseIsFirst -> start.crop
            else -> start.turned ?: start.crop.reshapedTo(screenAspectRatio)
        }
        base = CropState(mediaSize, screenAspectRatio, startCrop = baseStart, currentCrop = restored?.base ?: baseStart)
        val turnedStart = when {
            start == null -> base.crop().reshapedTo(1f / screenAspectRatio)
            baseIsFirst -> start.turned ?: start.crop.reshapedTo(1f / screenAspectRatio)
            else -> start.crop
        }
        turned = CropState(mediaSize, 1f / screenAspectRatio, startCrop = turnedStart, currentCrop = restored?.turned ?: turnedStart)
    }

    var isTurned: Boolean by mutableStateOf(restored?.isTurned ?: false)
        private set

    private var ownBase: Boolean by mutableStateOf(restored?.ownBase ?: startOwnBase)
    private var ownTurned: Boolean by mutableStateOf(restored?.ownTurned ?: startOwnTurned)

    /** The crop the editor shows now. */
    val active: CropState get() = if (isTurned) turned else base

    /** Whether the other crop than the first one is the user's own: what the turn button's dot says. */
    val hasOwnTurned: Boolean get() = if (baseIsFirst) ownTurned else ownBase

    /** Whether either crop is anywhere but where the editor opened it, or became the user's own or derived. */
    val isChanged: Boolean by derivedStateOf {
        ownBase != startOwnBase || ownTurned != startOwnTurned ||
            (ownBase && base.isChanged) || (ownTurned && turned.isChanged)
    }

    /** Shows the other way the screen can be turned; a crop that isn't the user's own is derived anew. */
    fun turn() {
        val (from, to, isOwn) = if (isTurned) Triple(turned, base, ownBase) else Triple(base, turned, ownTurned)
        if (!isOwn) to.seat(from.crop().reshapedTo(to.screenAspectRatio))
        isTurned = !isTurned
    }

    /** A gesture on the crop shown: from now on it is the user's own. */
    fun onGestureStart() {
        if (isTurned) ownTurned = true else ownBase = true
        active.onGestureStart()
    }

    /** Both crops back to where the editor opened, own or derived as they were. */
    fun reset(scope: CoroutineScope) {
        ownBase = startOwnBase
        ownTurned = startOwnTurned
        base.reset(scope)
        turned.reset(scope)
    }

    /** What to save: the first crop, and the other one where it is the user's own. */
    fun framing(): Framing {
        val first = if (baseIsFirst) base else turned
        val other = if (baseIsFirst) turned else base
        return Framing(crop = first.crop(), turned = other.crop().takeIf { hasOwnTurned })
    }

    private fun CropRect.reshapedTo(aspect: Float) = reshaped(aspect, mediaSize.width, mediaSize.height)

    /** What survives the activity's recreation: both crops, whose they are, and the way the editor shows. */
    internal data class Restored(
        val base: CropRect,
        val turned: CropRect,
        val ownBase: Boolean,
        val ownTurned: Boolean,
        val isTurned: Boolean,
    ) {
        /** The same, from a screen turned the other way since: what was turned is the base now. */
        fun swapped() = Restored(turned, base, ownTurned, ownBase, !isTurned)
    }

    companion object {
        private val CropRect.isPortrait: Boolean get() = width <= height

        private fun CropRect.toSaveable(): ArrayList<Any> = arrayListOf(left, top, right, bottom, rotation)

        private fun Any?.toCropRect(): CropRect = (this as List<*>).let { values ->
            CropRect(values[0] as Int, values[1] as Int, values[2] as Int, values[3] as Int, values[4] as Float)
        }

        /**
         * Keeps both crops across the activity's recreation and process death. A large screen turns the app
         * itself: restored on a screen turned since, the crops swap places, so that each stays with its way.
         */
        fun saver(mediaSize: IntSize, screenAspectRatio: Float, start: Framing?): Saver<FramingState, Any> = Saver(
            save = { state ->
                arrayListOf(
                    state.screenAspectRatio,
                    state.base.crop().toSaveable(),
                    state.turned.crop().toSaveable(),
                    state.ownBase,
                    state.ownTurned,
                    state.isTurned,
                )
            },
            restore = { saved ->
                val values = saved as List<*>
                val restored = Restored(
                    base = values[1].toCropRect(),
                    turned = values[2].toCropRect(),
                    ownBase = values[3] as Boolean,
                    ownTurned = values[4] as Boolean,
                    isTurned = values[5] as Boolean,
                )
                val wasTurned = abs(ln((values[0] as Float) * screenAspectRatio)) < abs(ln((values[0] as Float) / screenAspectRatio))
                FramingState(mediaSize, screenAspectRatio, start, if (wasTurned) restored.swapped() else restored)
            },
        )
    }
}

/** A new state for each media and screen; [start] is what the editor opened with, null for a new wallpaper. */
@Composable
internal fun rememberFramingState(mediaSize: IntSize, screenAspectRatio: Float, start: Framing?): FramingState =
    rememberSaveable(mediaSize, screenAspectRatio, start, saver = FramingState.saver(mediaSize, screenAspectRatio, start)) {
        FramingState(mediaSize, screenAspectRatio, start)
    }
