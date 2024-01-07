package com.onthecrow.wallper.gl

import android.graphics.Rect

class UVCoords(
    val topLeft: UVPoint,
    val topRight: UVPoint,
    val bottomLeft: UVPoint,
    val bottomRight: UVPoint,
) {
    companion object {
        fun fromRect(originalWidth: Int, originalHeight: Int, rect: Rect, rotation: Int): UVCoords {
            val (width, height) = if (rotation == 90 || rotation == 270) {
                originalHeight to originalWidth
            } else {
                originalWidth to originalHeight
            }
            // TODO clean this up via smart algorithm or math
            val pointsList =
                when (rotation) {
                    90 -> listOf(
                        UVPoint(
                            rect.top.toFloat() / height.toFloat(),
                            rect.right.toFloat() / width.toFloat(),
                        ),
                        UVPoint(
                            rect.top.toFloat() / height.toFloat(),
                            rect.left.toFloat() / width.toFloat(),
                        ),
                        UVPoint(
                            rect.bottom.toFloat() / height.toFloat(),
                            rect.right.toFloat() / width.toFloat(),
                        ),
                        UVPoint(
                            rect.bottom.toFloat() / height.toFloat(),
                            rect.left.toFloat() / width.toFloat(),
                        ),
                    )

                    180 -> listOf(
                        UVPoint(
                            rect.right.toFloat() / width.toFloat(),
                            rect.bottom.toFloat() / height.toFloat(),
                        ),
                        UVPoint(
                            rect.left.toFloat() / width.toFloat(),
                            rect.bottom.toFloat() / height.toFloat(),
                        ),
                        UVPoint(
                            rect.right.toFloat() / width.toFloat(),
                            rect.top.toFloat() / height.toFloat(),
                        ),
                        UVPoint(
                            rect.left.toFloat() / width.toFloat(),
                            rect.top.toFloat() / height.toFloat(),
                        ),
                    )

                    270 -> listOf(
                        UVPoint(
                            rect.bottom.toFloat() / height.toFloat(),
                            rect.left.toFloat() / width.toFloat(),
                        ),
                        UVPoint(
                            rect.bottom.toFloat() / height.toFloat(),
                            rect.right.toFloat() / width.toFloat(),
                        ),
                        UVPoint(
                            rect.top.toFloat() / height.toFloat(),
                            rect.left.toFloat() / width.toFloat(),
                        ),
                        UVPoint(
                            rect.top.toFloat() / height.toFloat(),
                            rect.right.toFloat() / width.toFloat(),
                        ),
                    )

                    else -> listOf(
                        UVPoint(
                            rect.left.toFloat() / width.toFloat(),
                            rect.top.toFloat() / height.toFloat(),
                        ),
                        UVPoint(
                            rect.right.toFloat() / width.toFloat(),
                            rect.top.toFloat() / height.toFloat(),
                        ),
                        UVPoint(
                            rect.left.toFloat() / width.toFloat(),
                            rect.bottom.toFloat() / height.toFloat(),
                        ),
                        UVPoint(
                            rect.right.toFloat() / width.toFloat(),
                            rect.bottom.toFloat() / height.toFloat(),
                        ),
                    )
                }
            return UVCoords(
                topLeft = pointsList[0],
                topRight = pointsList[1],
                bottomLeft = pointsList[2],
                bottomRight = pointsList[3],
            )
        }
    }
}

data class UVPoint(val u: Float, val v: Float)