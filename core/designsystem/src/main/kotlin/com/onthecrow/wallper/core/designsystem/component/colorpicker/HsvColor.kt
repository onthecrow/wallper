package com.onthecrow.wallper.core.designsystem.component.colorpicker

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A colour as a [ColorPicker] holds it: the angle of its [hue] in degrees (0 and [MAX_HUE] are both red),
 * its [saturation] (0 is grey), its [value] (0 is black) and its [alpha] (0 is transparent), each of the
 * last three from 0 to 1.
 *
 * The picker keeps these rather than red, green and blue: a grey has no hue there and a black no saturation,
 * so its thumbs would jump back as the colour passed through one.
 */
@Immutable
data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val alpha: Float = 1f,
) {
    /** The colour as Android packs it, alpha in the top byte. */
    fun toArgb(): Int {
        val sector = (hue % MAX_HUE + MAX_HUE) % MAX_HUE / SECTOR
        val chroma = value * saturation
        // The second strongest channel: it rises and falls across each pair of sectors.
        val second = chroma * (1 - abs(sector % 2 - 1))
        val floor = value - chroma
        val red: Float
        val green: Float
        val blue: Float
        when (sector.toInt()) {
            0 -> { red = chroma; green = second; blue = 0f }
            1 -> { red = second; green = chroma; blue = 0f }
            2 -> { red = 0f; green = chroma; blue = second }
            3 -> { red = 0f; green = second; blue = chroma }
            4 -> { red = second; green = 0f; blue = chroma }
            else -> { red = chroma; green = 0f; blue = second }
        }
        return (alpha.toChannel() shl 24) or
            ((red + floor).toChannel() shl 16) or
            ((green + floor).toChannel() shl 8) or
            (blue + floor).toChannel()
    }

    companion object {
        const val MAX_HUE = 360f

        /** The hue circle is six sectors, each between a primary colour and the next mix of two. */
        private const val SECTOR = 60f

        /** [argb] as the picker holds it; a grey gets the hue of red and a black no saturation. */
        fun fromArgb(argb: Int): HsvColor {
            val red = (argb shr 16 and 0xFF) / 255f
            val green = (argb shr 8 and 0xFF) / 255f
            val blue = (argb and 0xFF) / 255f
            val max = maxOf(red, green, blue)
            val delta = max - minOf(red, green, blue)
            val hue = when {
                delta == 0f -> 0f
                max == red -> SECTOR * ((green - blue) / delta).mod(6f)
                max == green -> SECTOR * ((blue - red) / delta + 2f)
                else -> SECTOR * ((red - green) / delta + 4f)
            }
            return HsvColor(
                hue = hue,
                saturation = if (max == 0f) 0f else delta / max,
                value = max,
                alpha = (argb ushr 24) / 255f,
            )
        }

        /** A channel of 0 to 1 as its byte. */
        private fun Float.toChannel(): Int = (this * 255f).roundToInt().coerceIn(0, 255)
    }
}
