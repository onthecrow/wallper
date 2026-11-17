package com.onthecrow.wallper.core.model

/**
 * A colour a wallpaper is seen through, like tinted glass: it only ever darkens the picture, so that icons and
 * text over the wallpaper stand out. Every channel of the picture is multiplied by the tint's factor for it,
 * `1 - alpha * (1 - colour)`, in the picture's own (gamma) values: white leaves the picture as it is, a darker
 * or a more saturated colour takes more away, and [alpha] is how much of that is done. Black at full alpha
 * leaves nothing.
 *
 * [argb] is the colour as Android packs it, alpha in the top byte. A wallpaper without a tint has `null`,
 * never a transparent one: see [orNull].
 */
@JvmInline
value class Tint(val argb: Int) {
    /** How strongly the colour is applied, 0 to 1. */
    val alpha: Float get() = (argb ushr 24) / 255f

    /** What the picture's red is multiplied by, 0 to 1; [greenFactor] and [blueFactor] likewise. */
    val redFactor: Float get() = factor(argb shr 16 and 0xFF)
    val greenFactor: Float get() = factor(argb shr 8 and 0xFF)
    val blueFactor: Float get() = factor(argb and 0xFF)

    /**
     * The three factors as one opaque packed colour: what to multiply a picture by where colours are drawn
     * rather than computed (a canvas, a layer's blend mode).
     */
    val multiplierArgb: Int
        get() = (0xFF shl 24) or (redFactor.toByte255() shl 16) or (greenFactor.toByte255() shl 8) or blueFactor.toByte255()

    /** This tint, or null if it changes nothing: a fully transparent one. */
    fun orNull(): Tint? = takeIf { argb ushr 24 != 0 }

    private fun factor(channel: Int): Float = 1 - alpha * (1 - channel / 255f)

    private fun Float.toByte255(): Int = (this * 255f + 0.5f).toInt().coerceIn(0, 255)
}
