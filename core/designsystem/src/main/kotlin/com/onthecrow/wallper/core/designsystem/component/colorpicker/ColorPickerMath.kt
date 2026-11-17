package com.onthecrow.wallper.core.designsystem.component.colorpicker

// The colour picker's geometry, free of Compose so it runs on the JVM. Distances are pixels from the
// picker's top left corner.

/** The parts of a [ColorPicker] a touch lands on. */
internal enum class PickerPart { SaturationValue, Hue, Alpha }

/**
 * Where the parts of a [ColorPicker] are in its [width] x [height] box: the saturation and value area on
 * top, the hue bar under it and the alpha bar at the bottom, each [barHeight] high, with no gap between
 * them. They are inset from the box ([insetX] on either side, [insetTop], [insetBottom]) by as much as a
 * thumb at an edge reaches out, so the thumbs stay in the box and a touch on any of them is the picker's.
 */
internal class ColorPickerGeometry(
    width: Float,
    height: Float,
    barHeight: Float,
    insetX: Float,
    insetTop: Float,
    insetBottom: Float,
) {
    val left = insetX
    val right = maxOf(left, width - insetX)
    val alphaBottom = maxOf(insetTop, height - insetBottom)
    val alphaTop = maxOf(insetTop, alphaBottom - barHeight)
    val hueTop = maxOf(insetTop, alphaTop - barHeight)

    /** The top of the saturation and value area, which ends at [hueTop]. */
    val top = insetTop

    /** The part a touch at [y] starts on: a drag stays with it wherever it goes. */
    fun partAt(y: Float): PickerPart = when {
        y < hueTop -> PickerPart.SaturationValue
        y < alphaTop -> PickerPart.Hue
        else -> PickerPart.Alpha
    }

    /** [color] with what [part] sets put where a touch at [x], [y] points; outside the part, at its nearest edge. */
    fun colorAt(part: PickerPart, x: Float, y: Float, color: HsvColor): HsvColor = when (part) {
        PickerPart.SaturationValue -> color.copy(saturation = across(x), value = 1 - fraction(y, top, hueTop))
        PickerPart.Hue -> color.copy(hue = across(x) * HsvColor.MAX_HUE)
        PickerPart.Alpha -> color.copy(alpha = across(x))
    }

    /** Where the area's thumb is for [color]: white on the left, the pure hue on the right. */
    fun saturationX(color: HsvColor): Float = along(color.saturation)

    /** Black at the bottom. */
    fun valueY(color: HsvColor): Float = top + (1 - color.value.coerceIn(0f, 1f)) * (hueTop - top)

    fun hueX(color: HsvColor): Float = along(color.hue / HsvColor.MAX_HUE)

    /** Transparent on the left. */
    fun alphaX(color: HsvColor): Float = along(color.alpha)

    private fun across(x: Float) = fraction(x, left, right)

    private fun along(fraction: Float) = left + fraction.coerceIn(0f, 1f) * (right - left)

    /** How far [at] is from [from] to [to], 0 to 1; 0 when there is no way between them. */
    private fun fraction(at: Float, from: Float, to: Float) =
        if (to > from) ((at - from) / (to - from)).coerceIn(0f, 1f) else 0f
}
