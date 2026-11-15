package com.onthecrow.wallper.core.model

/**
 * A colour laid over a wallpaper like a translucent sheet: every channel of the picture becomes
 * `picture * (1 - alpha) + colour * alpha`, in the picture's own (gamma) values, as a canvas draws a colour over
 * a bitmap. [argb] is the colour as Android packs it, alpha in the top byte.
 *
 * A wallpaper without a tint has `null`, never a transparent one: see [orNull].
 */
@JvmInline
value class Tint(val argb: Int) {
    /** How much of the colour shows, 0 to 1; the picture keeps the rest. */
    val alpha: Float get() = (argb ushr 24) / 255f
    val red: Float get() = (argb shr 16 and 0xFF) / 255f
    val green: Float get() = (argb shr 8 and 0xFF) / 255f
    val blue: Float get() = (argb and 0xFF) / 255f

    /** This tint, or null if it changes nothing: a fully transparent one. */
    fun orNull(): Tint? = takeIf { argb ushr 24 != 0 }

    /** A [channel] of the picture, 0 to 1, with a [color] channel of this tint over it. */
    fun over(channel: Float, color: Float): Float = channel * (1 - alpha) + color * alpha
}
