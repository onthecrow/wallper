package com.onthecrow.wallper.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color

/**
 * Lays a wallpaper's tint over its picture, as the wallpaper has it on screen: [argb] is the colour as Android
 * packs it, alpha in the top byte, drawn over the content. A wallpaper's thumbnail never has the tint in it, so
 * whatever shows one adds this. Null is no tint.
 */
fun Modifier.wallpaperTint(argb: Int?): Modifier =
    if (argb == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawRect(Color(argb))
        }
    }
