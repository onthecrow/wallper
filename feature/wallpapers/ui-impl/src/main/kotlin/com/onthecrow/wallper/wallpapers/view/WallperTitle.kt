package com.onthecrow.wallper.wallpapers.view

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.wallpapers.ui.R
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * The list's title, the brush lettering «Wallper». While [writes] (Wallper is the phone's wallpaper) a brush
 * writes it as the splash's W is written, it stays [HOLD_MS], the brush takes it away from the left the
 * same way, and writes it again, round and round. Otherwise it is just there; null, before that is known,
 * it isn't yet, so that a title about to be written doesn't show first.
 *
 * The letters are drawn whole and kept only where the brush has gone ([TitleBrush]), in a layer of their
 * own: any background shows through where it hasn't.
 */
@Composable
internal fun WallperTitle(writes: Boolean?, modifier: Modifier = Modifier) {
    // How far along its way the brush has written the title, and how far it has taken it away again.
    val written = remember { Animatable(0f) }
    val erased = remember { Animatable(0f) }
    LaunchedEffect(writes) {
        when (writes) {
            null -> Unit
            false -> {
                erased.snapTo(0f)
                written.snapTo(1f)
            }
            true -> {
                // Set as the wallpaper while the title was there: it is taken away first.
                if (written.value == 1f && erased.value == 0f) {
                    delay(HOLD_MS)
                    erased.animateTo(1f, tween(ERASE_MS, easing = FastOutSlowInEasing))
                }
                while (true) {
                    erased.snapTo(0f)
                    written.snapTo(0f)
                    written.animateTo(1f, tween(WRITE_MS, easing = FastOutSlowInEasing))
                    delay(HOLD_MS)
                    erased.animateTo(1f, tween(ERASE_MS, easing = FastOutSlowInEasing))
                }
            }
        }
    }
    Image(
        painter = painterResource(id = R.drawable.ic_wallper_title),
        contentDescription = stringResource(id = DesignR.string.app_name),
        modifier = modifier.brushed(from = { erased.value }, to = { written.value }),
        colorFilter = ColorFilter.tint(color = LocalContentColor.current),
    )
}

/**
 * Keeps the content only where [TitleBrush] went between [from] and [to], read while drawing. All of the way
 * draws it as it is, without a layer.
 */
private fun Modifier.brushed(from: () -> Float, to: () -> Float): Modifier = this
    .graphicsLayer {
        val whole = from() <= 0f && to() >= 1f
        // The brush keeps or clears what is drawn in this layer only, not the bar behind it.
        compositingStrategy = if (whole) CompositingStrategy.Auto else CompositingStrategy.Offscreen
    }
    .drawWithContent {
        val start = from()
        val end = to()
        if (start <= 0f && end >= 1f) {
            drawContent()
            return@drawWithContent
        }
        if (end <= start) return@drawWithContent
        drawContent()
        // The painter's viewport fitted into the bounds and centred, as the image draws it.
        val scale = min(size.width / TitleBrush.VIEWPORT_WIDTH, size.height / TitleBrush.VIEWPORT_HEIGHT)
        val left = (size.width - TitleBrush.VIEWPORT_WIDTH * scale) / 2
        val top = (size.height - TitleBrush.VIEWPORT_HEIGHT * scale) / 2
        drawIntoCanvas { canvas ->
            // The brush's strokes are the mask: the layer keeps the letters where they are and clears the rest.
            canvas.saveLayer(size.toRect(), MaskPaint)
            TitleBrush.trace(start, end) { x0, y0, x1, y1, reach ->
                drawLine(
                    color = Color.Black,
                    start = Offset(left + x0 * scale, top + y0 * scale),
                    end = Offset(left + x1 * scale, top + y1 * scale),
                    strokeWidth = 2 * reach * scale,
                    cap = StrokeCap.Round,
                )
            }
            canvas.restore()
        }
    }

private val MaskPaint = Paint().apply { blendMode = BlendMode.DstIn }

/** The splash's W takes 900 ms; the word is longer. */
private const val WRITE_MS = 1_800
private const val HOLD_MS = 2_000L
private const val ERASE_MS = 1_000
