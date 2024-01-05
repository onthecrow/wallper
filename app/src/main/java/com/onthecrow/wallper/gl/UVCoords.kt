package com.onthecrow.wallper.gl

import android.graphics.Rect

class UVCoords(
    val topLeft: UVPoint,
    val topRight: UVPoint,
    val bottomLeft: UVPoint,
    val bottomRight: UVPoint,
) {
    companion object {
        fun fromRect(originalWidth: Int, originalHeight: Int, rect: Rect): UVCoords {
            return UVCoords(
                topLeft = UVPoint(
                    rect.left.toFloat() / originalWidth.toFloat(),
                    rect.top.toFloat() / originalHeight.toFloat(),
                ),
                topRight = UVPoint(
                    rect.right.toFloat() / originalWidth.toFloat(),
                    rect.top.toFloat() / originalHeight.toFloat(),
                ),
                bottomLeft = UVPoint(
                    rect.left.toFloat() / originalWidth.toFloat(),
                    rect.bottom.toFloat() / originalHeight.toFloat(),
                ),
                bottomRight = UVPoint(
                    rect.right.toFloat() / originalWidth.toFloat(),
                    rect.bottom.toFloat() / originalHeight.toFloat(),
                ),
            )
        }
    }
}

class UVPoint(val u: Float, val v: Float)