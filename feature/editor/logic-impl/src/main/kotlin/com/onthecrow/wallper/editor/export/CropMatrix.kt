package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.CropRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * [crop] as one matrix from the input frame's normalized device coordinates (both axes -1 to 1, y pointing
 * up) to the output's: the crop, turned by its rotation, fills the output. [crop] is in pixels of the upright
 * [frameWidth] x [frameHeight] frame.
 */
internal class CropMatrix(crop: CropRect, frameWidth: Float, frameHeight: Float) {

    // In pixels with y down: the output, centred on the crop, is the frame turned clockwise by the rotation
    // and scaled to NDC, y flipped back up. See CropMatrixTest for the corners.
    private val radians = Math.toRadians(crop.rotation.toDouble())
    private val cos = cos(radians).toFloat()
    private val sin = sin(radians).toFloat()
    private val outputWidth = crop.width.toFloat()
    private val outputHeight = crop.height.toFloat()

    /** Input NDC to output NDC: x' = [xx] x + [xy] y + [tx], y' = [yx] x + [yy] y + [ty]. */
    val xx = cos * frameWidth / outputWidth
    val xy = sin * frameHeight / outputWidth
    val yx = -sin * frameWidth / outputHeight
    val yy = cos * frameHeight / outputHeight
    val tx: Float
    val ty: Float

    init {
        // From the crop's centre to the frame's, in pixels with y down.
        val dx = frameWidth / 2 - crop.centerX
        val dy = frameHeight / 2 - crop.centerY
        tx = 2 * (cos * dx - sin * dy) / outputWidth
        ty = -2 * (sin * dx + cos * dy) / outputHeight
    }

    /** The same as a 4x4 column-major GL matrix. */
    fun toGlArray(): FloatArray = floatArrayOf(
        xx, yx, 0f, 0f,
        xy, yy, 0f, 0f,
        0f, 0f, 1f, 0f,
        tx, ty, 0f, 1f,
    )
}
