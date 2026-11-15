package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.Tint

/**
 * [tint] as the matrix the export multiplies each pixel's `(r, g, b, 1)` by: a channel keeps `1 - alpha` of
 * itself ([keep]) and gets `alpha` of the tint's ([addRed], [addGreen], [addBlue]), which is [Tint.over].
 */
internal class TintMatrix(tint: Tint) {
    val keep = 1 - tint.alpha
    val addRed = tint.red * tint.alpha
    val addGreen = tint.green * tint.alpha
    val addBlue = tint.blue * tint.alpha

    /** The same as a 4x4 column-major GL matrix. */
    fun toGlArray(): FloatArray = floatArrayOf(
        keep, 0f, 0f, 0f,
        0f, keep, 0f, 0f,
        0f, 0f, keep, 0f,
        addRed, addGreen, addBlue, 1f,
    )
}
