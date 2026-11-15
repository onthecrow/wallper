package com.onthecrow.wallper.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color

/**
 * Shows a wallpaper's picture through its tint, as the wallpaper is on screen: everything drawn so far where
 * the content is, the content included, is multiplied by [multiplierArgb], an opaque colour as Android packs
 * it (`Tint.multiplierArgb`). A wallpaper's thumbnail never has the tint in it, so whatever shows one adds
 * this. Put on an empty box over a picture, it tints the picture under the box. Null is no tint.
 */
fun Modifier.wallpaperTint(multiplierArgb: Int?): Modifier =
    if (multiplierArgb == null) {
        this
    } else {
        drawWithContent {
            drawContent()
            drawRect(Color(multiplierArgb), blendMode = BlendMode.Modulate)
        }
    }
