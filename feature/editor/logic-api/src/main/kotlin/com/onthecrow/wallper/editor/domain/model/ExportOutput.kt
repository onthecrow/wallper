package com.onthecrow.wallper.editor.domain.model

import kotlin.math.roundToInt

/**
 * What an export makes: a picture [width] x [height] pixels as it is shown, [durationMs] long, in [codec]
 * (its name as people know it: «H.264»).
 */
data class ExportOutput(val width: Int, val height: Int, val durationMs: Long, val codec: String) {
    /** The same at [height], in the same proportions: what an encoder that can't take the size makes instead. */
    fun withHeight(height: Int): ExportOutput {
        if (height <= 0 || height == this.height || this.height <= 0) return this
        return copy(width = (width.toFloat() * height / this.height).roundToInt(), height = height)
    }
}
