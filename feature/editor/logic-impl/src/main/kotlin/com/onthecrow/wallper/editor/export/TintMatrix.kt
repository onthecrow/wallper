package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.Tint

/**
 * [tint] as the matrix the export multiplies each pixel's `(r, g, b, 1)` by: every channel times the tint's
 * factor for it, as the wallpaper's renderers do in their shaders.
 */
internal class TintMatrix(private val tint: Tint) {

    /** A 4x4 column-major GL matrix. */
    fun toGlArray(): FloatArray = floatArrayOf(
        tint.redFactor, 0f, 0f, 0f,
        0f, tint.greenFactor, 0f, 0f,
        0f, 0f, tint.blueFactor, 0f,
        0f, 0f, 0f, 1f,
    )
}
