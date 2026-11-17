package com.onthecrow.wallper.editor.export

import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.Framing
import com.onthecrow.wallper.core.model.reshaped
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What an optimised video exports of its frame, [rect] (turned like the crop, as [CropMatrix] takes it), and
 * the [framing] of the export: its windows in pixels of the export, [rect]'s width x height.
 */
internal data class ExportArea(val rect: CropRect, val framing: Framing) {
    val width: Int get() = rect.width
    val height: Int get() = rect.height

    companion object {
        /**
         * The smallest rect, turned like [framing]'s crop, that holds the windows of both ways the screen can
         * be turned in the [frameWidth] x [frameHeight] frame: the crop, and the turned one or the one derived
         * from the crop. A turned crop derived here comes back null, to be derived the same way in the export.
         */
        fun of(framing: Framing, frameWidth: Int, frameHeight: Int): ExportArea {
            val crop = framing.crop
            val turned = framing.turned
                ?: crop.reshaped(aspect = crop.height.toFloat() / crop.width, frameWidth, frameHeight)
            val turn = Turn(crop.rotation)
            // Every corner of both windows, from the crop's centre along the export's axes.
            val corners = listOf(crop, turned).flatMap { window -> window.corners().map { turn.toExport(it, crop) } }
            val minX = corners.minOf { it.first }
            val maxX = corners.maxOf { it.first }
            val minY = corners.minOf { it.second }
            val maxY = corners.maxOf { it.second }
            val width = ceil(maxX - minX).toInt()
            val height = ceil(maxY - minY).toInt()
            val (centerX, centerY) = turn.toFrame((minX + maxX) / 2, (minY + maxY) / 2, crop)
            val left = (centerX - width / 2.0).roundToInt()
            val top = (centerY - height / 2.0).roundToInt()
            val rect = CropRect(left, top, left + width, top + height, crop.rotation)
            return ExportArea(
                rect = rect,
                framing = Framing(
                    crop = crop.inExportOf(rect),
                    turned = framing.turned?.inExportOf(rect),
                ),
            )
        }

        /** Only [framing]'s crop, upright, as before both ways were exported: the export is the crop. */
        fun cropOnly(framing: Framing): ExportArea = ExportArea(
            rect = framing.crop,
            framing = Framing(CropRect(0, 0, framing.crop.width, framing.crop.height)),
        )

        /** This window in pixels of an export of [area]: from the area's top left along its turned axes. */
        private fun CropRect.inExportOf(area: CropRect): CropRect {
            val (x, y) = Turn(area.rotation).toExport(centerX.toDouble() to centerY.toDouble(), area)
            val exportX = area.width / 2.0 + x
            val exportY = area.height / 2.0 + y
            val left = (exportX - width / 2.0).roundToInt()
            val top = (exportY - height / 2.0).roundToInt()
            return CropRect(left, top, left + width, top + height, rotation - area.rotation)
        }

        /** The four corners of this window in pixels of the frame, turned by its rotation ([CropRect]). */
        private fun CropRect.corners(): List<Pair<Double, Double>> {
            val turn = Turn(rotation)
            return listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).map { (sx, sy) ->
                turn.toFrame(sx * width / 2.0, sy * height / 2.0, this)
            }
        }
    }

    /**
     * The turn of a crop's [rotation] between the frame and the screen (here the export): a point the screen
     * shows [dx], [dy] from a crop's centre is that far along axes turned by -rotation in the frame.
     */
    private class Turn(rotation: Float) {
        private val radians = Math.toRadians(rotation.toDouble())
        private val cos = cos(radians)
        private val sin = sin(radians)

        /** From the screen's offset off [around]'s centre to the frame. */
        fun toFrame(dx: Double, dy: Double, around: CropRect): Pair<Double, Double> =
            (around.centerX + dx * cos + dy * sin) to (around.centerY - dx * sin + dy * cos)

        /** From a [point] of the frame to the screen's offset off [around]'s centre. */
        fun toExport(point: Pair<Double, Double>, around: CropRect): Pair<Double, Double> {
            val x = point.first - around.centerX
            val y = point.second - around.centerY
            return (x * cos - y * sin) to (x * sin + y * cos)
        }
    }
}
