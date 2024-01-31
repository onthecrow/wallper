package com.onthecrow.wallper.crop

import android.graphics.Rect

data class NDCCoords(val left: Float, val right: Float, val bottom: Float, val top: Float) {
    companion object {
        fun fromRect(rect: Rect, frameWidth: Float, frameHeight: Float): NDCCoords {
            val left = if (rect.left < (frameWidth / 2)) {
                -(1 - (rect.left / (frameWidth / 2)))
            } else {
                (rect.left - frameWidth / 2) / (frameWidth / 2)
            }
            val right = if (rect.right < (frameWidth / 2)) {
                -(rect.right / (frameWidth / 2))
            } else {
                (rect.right - frameWidth / 2) / (frameWidth / 2)
            }
            val top = if (rect.top < (frameHeight / 2)) {
                1 - (rect.top / (frameHeight / 2))
            } else {
                -((rect.top - frameHeight / 2) / (frameHeight / 2))
            }
            val bottom = if (rect.bottom < (frameHeight / 2)) {
                rect.bottom / (frameHeight / 2)
            } else {
                -((rect.bottom - frameHeight / 2) / (frameHeight / 2))
            }
            return NDCCoords(left, right, bottom, top)
        }
    }
}