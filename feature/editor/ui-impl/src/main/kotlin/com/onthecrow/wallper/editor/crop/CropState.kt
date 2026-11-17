package com.onthecrow.wallper.editor.crop

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The picture's place under the crop frame. Gestures move it freely, except that its angle snaps level and
 * its centre to the frame's middle ([CropGeometry.snapped]); when they end it springs to the nearest place that
 * covers the whole frame ([CropGeometry.covering]).
 *
 * Until the frame is laid out there is no [placement]; [crop] then gives the crop to start from.
 *
 * @param startCrop the crop the editor opened with, null for the middle of the media: [reset] goes back to it.
 * @param currentCrop where the frame starts, if not at [startCrop]: a crop restored after the activity was
 *     recreated.
 */
@Stable
internal class CropState(
    /** The media's upright size in pixels. */
    val mediaSize: IntSize,
    /** The frame's opening has the proportions of the screen the wallpaper is drawn on. */
    val screenAspectRatio: Float,
    private val startCrop: CropRect?,
    currentCrop: CropRect? = startCrop,
) {
    private var geometry: CropGeometry? = null
    private var frameWidth = 0f
    private var frameHeight = 0f

    /** Null until the frame is laid out. */
    var placement: Placement? by mutableStateOf(null)
        private set

    /** Where [startCrop] puts the picture in the current frame; null until it is laid out. */
    private var startPlacement: Placement? by mutableStateOf(null)

    /** Where the picture is springing to, null while fingers hold it. */
    private var settleTarget: Placement? by mutableStateOf(null)

    /** The crop to start from, given back exactly until the user moves the picture. */
    private var untouchedCrop: CropRect? by mutableStateOf(currentCrop)

    /** True from the first finger down to the last one up. */
    var isInteracting: Boolean by mutableStateOf(false)
        private set

    /**
     * The snap lines to show while fingers move the picture: level only once they have turned it, the centre
     * ones as soon as it moves. None between gestures.
     */
    var guides: Snaps by mutableStateOf(Snaps.NONE)
        private set

    /** Whether the picture is anywhere but where the editor opened it, once it springs back; false from a reset on. */
    val isChanged: Boolean by derivedStateOf {
        val start = startPlacement
        val placement = placement
        val geometry = geometry
        when {
            untouchedCrop == startCrop && untouchedCrop != null -> false
            start == null || placement == null || geometry == null -> untouchedCrop != startCrop
            else -> !(settleTarget ?: geometry.covering(placement)).isNear(start)
        }
    }

    /** Where the fingers alone put the picture in this gesture, before snapping. */
    private var fingers: Placement? = null
    private var snaps = Snaps.NONE
    private var hasTurned = false

    private var settling: Job? = null

    /** The crop the frame shows now: what to save. */
    fun crop(): CropRect {
        untouchedCrop?.let { return it }
        val geometry = geometry
        val placement = placement
        if (geometry != null && placement != null) return geometry.toCrop(geometry.covering(placement))
        return CropGeometry(mediaSize.width.toFloat(), mediaSize.height.toFloat(), screenAspectRatio, 1f)
            .run { toCrop(initial) }
    }

    /** The frame's opening is [width] x [height] view pixels; it keeps the crop it shows when it changes size. */
    fun onFrameSize(width: Float, height: Float) {
        if (width == frameWidth && height == frameHeight) return
        if (width <= 0f || height <= 0f || mediaSize.width <= 0 || mediaSize.height <= 0) return
        frameWidth = width
        frameHeight = height
        val crop = if (placement != null) crop() else untouchedCrop
        settling?.cancel()
        val newGeometry = CropGeometry(mediaSize.width.toFloat(), mediaSize.height.toFloat(), width, height)
        geometry = newGeometry
        startPlacement = startCrop?.let(newGeometry::fromCrop) ?: newGeometry.initial
        placement = crop?.let(newGeometry::fromCrop) ?: newGeometry.initial
        settleTarget = null
    }

    fun onGestureStart() {
        settling?.cancel()
        settleTarget = null
        untouchedCrop = null
        isInteracting = true
        fingers = placement
        // Already in place is no snap: no tick for a picture that starts centred or level.
        snaps = placement?.let { geometry?.snapsOf(it) } ?: Snaps.NONE
        hasTurned = false
    }

    /**
     * One step of a gesture, see [CropGeometry.transformed]; the centroid is from the frame's centre.
     * Returns true when the picture has just snapped: level, or its centre to the frame's middle.
     */
    fun onGesture(centroidX: Float, centroidY: Float, panX: Float, panY: Float, zoom: Float, rotation: Float): Boolean {
        val geometry = geometry ?: return false
        val from = fingers ?: placement ?: return false
        val moved = geometry.transformed(from, centroidX, centroidY, panX, panY, zoom, rotation)
        fingers = moved
        if (rotation != 0f) hasTurned = true
        val snapped = geometry.snapped(moved)
        placement = snapped
        val now = geometry.snapsOf(snapped)
        val hasSnapped = now.gainedSince(snaps)
        snaps = now
        guides = now.copy(level = now.level && hasTurned)
        return hasSnapped
    }

    fun onGestureEnd(scope: CoroutineScope) {
        isInteracting = false
        guides = Snaps.NONE
        fingers = null
        val geometry = geometry ?: return
        val from = placement ?: return
        settleTo(geometry.covering(from), scope)
    }

    /** Back to where the editor opened, the short way round. */
    fun reset(scope: CoroutineScope) {
        untouchedCrop = startCrop
        val from = placement ?: return
        val start = startPlacement ?: return
        settleTo(start.copy(rotation = from.rotation + shortestTurn(from.rotation, start.rotation)), scope)
    }

    private fun settleTo(to: Placement, scope: CoroutineScope) {
        settling?.cancel()
        val from = placement ?: return
        settleTarget = to
        if (to == from) return
        settling = scope.launch {
            animate(PlacementConverter, from, to, animationSpec = spring(stiffness = SETTLE_STIFFNESS)) { value, _ ->
                placement = value
            }
        }
    }

    companion object {
        private const val SETTLE_STIFFNESS = 400f

        private val PlacementConverter = TwoWayConverter<Placement, AnimationVector4D>(
            convertToVector = { AnimationVector4D(it.scale, it.rotation, it.offsetX, it.offsetY) },
            convertFromVector = { Placement(it.v1, it.v2, it.v3, it.v4) },
        )

        /** The turn from [from] to [to] degrees, within half a turn either way. */
        private fun shortestTurn(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

        /** The same place to the eye: within a thousandth of the scale, a hundredth of a degree, half a pixel. */
        private fun Placement.isNear(other: Placement) =
            abs(scale - other.scale) <= scale * 1e-3f &&
                abs(shortestTurn(rotation, other.rotation)) < 0.01f &&
                abs(offsetX - other.offsetX) < 0.5f &&
                abs(offsetY - other.offsetY) < 0.5f

        private fun CropRect?.toSaveable(): ArrayList<Any>? = this?.let { arrayListOf(left, top, right, bottom, rotation) }

        private fun Any?.toCropRect(): CropRect? = (this as? List<*>)?.let { values ->
            CropRect(
                left = values[0] as Int,
                top = values[1] as Int,
                right = values[2] as Int,
                bottom = values[3] as Int,
                rotation = values[4] as Float,
            )
        }

        /** Keeps the crop, and the one to reset to, across configuration changes and process death. */
        fun saver(mediaSize: IntSize, screenAspectRatio: Float): Saver<CropState, Any> = Saver(
            save = { state -> arrayListOf(state.crop().toSaveable(), state.startCrop.toSaveable()) },
            restore = { saved ->
                val values = saved as List<*>
                CropState(mediaSize, screenAspectRatio, startCrop = values[1].toCropRect(), currentCrop = values[0].toCropRect())
            },
        )
    }
}

/** A new state for each media; [initialCrop] is where the frame starts, null for the middle of the media. */
@Composable
internal fun rememberCropState(mediaSize: IntSize, screenAspectRatio: Float, initialCrop: CropRect?): CropState =
    rememberSaveable(mediaSize, screenAspectRatio, saver = CropState.saver(mediaSize, screenAspectRatio)) {
        CropState(mediaSize, screenAspectRatio, initialCrop)
    }
