package com.onthecrow.wallper.editor.crop

import com.onthecrow.wallper.core.model.CropRect
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where the picture lies under the crop frame: turned clockwise by [rotation] degrees about its centre,
 * [scale] view pixels per media pixel, its centre [offsetX], [offsetY] view pixels from the frame's centre.
 */
internal data class Placement(
    val scale: Float,
    val rotation: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/** Which snaps hold [placement]: its angle level, its centre on the frame's vertical or horizontal middle. */
internal data class Snaps(val level: Boolean, val centerX: Boolean, val centerY: Boolean) {
    /** Whether any snap here wasn't in [before]: the moment for a tick. */
    fun gainedSince(before: Snaps): Boolean =
        (level && !before.level) || (centerX && !before.centerX) || (centerY && !before.centerY)

    companion object {
        val NONE = Snaps(level = false, centerX = false, centerY = false)
    }
}

/**
 * The maths between a [Placement] and the crop it makes, for upright media of [mediaWidth] x [mediaHeight]
 * pixels under a frame opening of [frameWidth] x [frameHeight] view pixels. Angles are clockwise on the screen,
 * which is y-down, so a turn by θ is (x cos θ - y sin θ, x sin θ + y cos θ).
 */
internal class CropGeometry(
    private val mediaWidth: Float,
    private val mediaHeight: Float,
    private val frameWidth: Float,
    private val frameHeight: Float,
) {

    /** The scale at which the unturned picture just covers the frame. */
    val baseScale: Float = max(frameWidth / mediaWidth, frameHeight / mediaHeight)

    private val maxScale = baseScale * MAX_ZOOM

    /** The picture centred and unturned, just covering the frame. */
    val initial: Placement get() = Placement(baseScale, 0f, 0f, 0f)

    /** How close to the frame's middle the picture's centre snaps to it, in view pixels. */
    private val centerSnapDistance = min(frameWidth, frameHeight) * CENTER_SNAP

    /**
     * [placement] as the fingers left it, snapped: its angle level within [LEVEL_SNAP_DEGREES] of a multiple
     * of 90° (turned about its own centre), its centre on the frame's middle within a few percent of the frame
     * along each axis.
     */
    fun snapped(placement: Placement): Placement = placement.copy(
        rotation = snappedToLevel(placement.rotation),
        offsetX = if (abs(placement.offsetX) <= centerSnapDistance) 0f else placement.offsetX,
        offsetY = if (abs(placement.offsetY) <= centerSnapDistance) 0f else placement.offsetY,
    )

    /** Which snaps hold [placement] exactly. */
    fun snapsOf(placement: Placement) = Snaps(
        level = isLevel(placement.rotation),
        centerX = abs(placement.offsetX) < CENTER_EPSILON,
        centerY = abs(placement.offsetY) < CENTER_EPSILON,
    )

    /**
     * [placement] after one step of a gesture: the fingers' [centroidX], [centroidY] (from the frame's centre)
     * moved by [panX], [panY], spread by [zoom] and turned by [rotation] degrees. The point under the fingers
     * stays under them. Scale may go past what [covering] allows, within limits, to spring back afterwards.
     */
    fun transformed(
        placement: Placement,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoom: Float,
        rotation: Float,
    ): Placement {
        val newRotation = placement.rotation + rotation
        val newScale = (placement.scale * zoom)
            .coerceIn(minScale(newRotation) * GESTURE_UNDERSHOOT, maxScale * GESTURE_OVERSHOOT)
        val (x, y) = turn(placement.offsetX - centroidX, placement.offsetY - centroidY, rotation)
        val actualZoom = newScale / placement.scale
        return Placement(
            scale = newScale,
            rotation = newRotation,
            offsetX = centroidX + panX + actualZoom * x,
            offsetY = centroidY + panY + actualZoom * y,
        )
    }

    /**
     * The placement nearest to [placement] that covers the whole frame, at its rotation: scaled up about the
     * frame's centre only as much as it must be (or down to the zoom limit), then moved as little as it must be.
     */
    fun covering(placement: Placement): Placement {
        val (halfBoundsX, halfBoundsY) = frameHalfBounds(placement.rotation)
        val minScale = minScale(placement.rotation)
        val scale = placement.scale.coerceIn(minScale, max(maxScale, minScale))
        val zoom = scale / placement.scale
        // The frame's centre in media pixels from the picture's centre, along the picture's own axes.
        val (centerX, centerY) = turn(-placement.offsetX * zoom / scale, -placement.offsetY * zoom / scale, -placement.rotation)
        val roomX = (mediaWidth / 2 - halfBoundsX / scale).coerceAtLeast(0f)
        val roomY = (mediaHeight / 2 - halfBoundsY / scale).coerceAtLeast(0f)
        val (offsetX, offsetY) = turn(
            -centerX.coerceIn(-roomX, roomX) * scale,
            -centerY.coerceIn(-roomY, roomY) * scale,
            placement.rotation,
        )
        return Placement(scale, placement.rotation, offsetX, offsetY)
    }

    /** The part of the media the frame shows at [placement], rounded to pixels, the rotation within ±180°. */
    fun toCrop(placement: Placement): CropRect {
        val (dx, dy) = turn(-placement.offsetX / placement.scale, -placement.offsetY / placement.scale, -placement.rotation)
        val centerX = mediaWidth / 2 + dx
        val centerY = mediaHeight / 2 + dy
        val halfWidth = frameWidth / placement.scale / 2
        val halfHeight = frameHeight / placement.scale / 2
        return CropRect(
            left = (centerX - halfWidth).roundToInt(),
            top = (centerY - halfHeight).roundToInt(),
            right = (centerX + halfWidth).roundToInt(),
            bottom = (centerY + halfHeight).roundToInt(),
            rotation = normalized(placement.rotation),
        )
    }

    /**
     * The placement that shows [crop], or [initial] for an empty one. Of a crop of other proportions than the
     * frame (chosen on another screen) the frame shows the middle, as large as fits.
     */
    fun fromCrop(crop: CropRect): Placement {
        if (crop.width <= 0 || crop.height <= 0) return initial
        val scale = max(frameWidth / crop.width, frameHeight / crop.height)
        val (offsetX, offsetY) = turn(
            -(crop.centerX - mediaWidth / 2) * scale,
            -(crop.centerY - mediaHeight / 2) * scale,
            crop.rotation,
        )
        return covering(Placement(scale, crop.rotation, offsetX, offsetY))
    }

    /** The smallest scale at which the media, turned by [rotation], can cover the frame. */
    private fun minScale(rotation: Float): Float {
        val (halfBoundsX, halfBoundsY) = frameHalfBounds(rotation)
        return max(2 * halfBoundsX / mediaWidth, 2 * halfBoundsY / mediaHeight)
    }

    /** Half the size of the frame's bounding box along the axes of the media turned by [rotation], in view pixels. */
    private fun frameHalfBounds(rotation: Float): Pair<Float, Float> {
        val radians = Math.toRadians(rotation.toDouble())
        val cos = abs(cos(radians)).toFloat()
        val sin = abs(sin(radians)).toFloat()
        return Pair(
            frameWidth / 2 * cos + frameHeight / 2 * sin,
            frameWidth / 2 * sin + frameHeight / 2 * cos,
        )
    }

    companion object {
        /** How far in the user can zoom, from [baseScale]. */
        const val MAX_ZOOM = 8f

        /** How close to level (a multiple of 90°) the fingers' angle snaps to it. */
        const val LEVEL_SNAP_DEGREES = 4f

        /** How close to the frame's middle the picture's centre snaps to it, in parts of the frame's shorter side. */
        const val CENTER_SNAP = 0.03f

        private const val CENTER_EPSILON = 0.01f

        /** How far past the limits a gesture may scale before the picture springs back. */
        private const val GESTURE_UNDERSHOOT = 0.5f
        private const val GESTURE_OVERSHOOT = 1.5f

        /** [x], [y] turned clockwise on the screen by [degrees]. */
        private fun turn(x: Float, y: Float, degrees: Float): Pair<Float, Float> {
            val radians = Math.toRadians(degrees.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            return Pair(x * cos - y * sin, x * sin + y * cos)
        }

        /** [degrees] snapped to the nearest multiple of 90° when it is within [LEVEL_SNAP_DEGREES] of it. */
        fun snappedToLevel(degrees: Float): Float {
            val level = (degrees / 90f).roundToInt() * 90f
            return if (abs(degrees - level) <= LEVEL_SNAP_DEGREES) level else degrees
        }

        /** Whether the picture's edges run along the frame's: [degrees] is a multiple of 90°. */
        fun isLevel(degrees: Float): Boolean = abs(degrees - (degrees / 90f).roundToInt() * 90f) < LEVEL_EPSILON

        private const val LEVEL_EPSILON = 1e-3f

        /** [degrees] in -180 until 180. */
        private fun normalized(degrees: Float): Float = ((degrees % 360f) + 540f) % 360f - 180f
    }
}
