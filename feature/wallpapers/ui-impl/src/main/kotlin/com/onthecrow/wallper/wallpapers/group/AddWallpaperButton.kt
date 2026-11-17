package com.onthecrow.wallper.wallpapers.group

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.onthecrow.wallper.core.designsystem.R as DesignR
import kotlinx.coroutines.launch

/**
 * «Add wallpaper». With [morphProgress] 0 it is a pill with a plus and the label; with 1 a round plus button.
 * In between the plus moves to the middle and the label slides out to the right. A press plays a short «pop»:
 * the button shrinks and springs back.
 */
@Composable
internal fun AddWallpaperButton(
    text: String,
    morphProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = morphProgress.coerceIn(0f, 1f)
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val labelStyle = MaterialTheme.typography.labelLarge

    val roundSize = AddWallpaperButtonDefaults.Size
    val width = AddWallpaperButtonDefaults.width(rememberAddWallpaperPillWidth(text), progress)
    val iconSize = lerp(PillIconSize, AddWallpaperButtonDefaults.IconSize, progress)
    val iconStart = lerp(PillStartPadding, (width - iconSize) / 2, progress)
    val labelStart = PillStartPadding + PillIconSize + PillIconTextGap + LabelExitDistance * progress

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val containerColor = lerp(
        colorScheme.secondaryContainer,
        if (isDark) colorScheme.secondary else colorScheme.surfaceContainerHighest,
        progress,
    )
    val contentColor = lerp(
        colorScheme.onSecondaryContainer,
        if (isDark) colorScheme.onSecondary else colorScheme.onSecondaryContainer,
        progress,
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .size(width = width, height = roundSize)
            .clip(CircleShape)
            .background(containerColor)
            .clickable {
                scope.launch {
                    pop.snapTo(1f)
                    pop.animateTo(POP_SCALE, tween(POP_DOWN_MS))
                    pop.animateTo(1f, tween(POP_UP_MS))
                }
                onClick()
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = labelStyle,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .offset(x = labelStart)
                .graphicsLayer { alpha = (1f - progress * 2f).coerceAtLeast(0f) },
        )
        Icon(
            painter = painterResource(DesignR.drawable.ic_plus),
            contentDescription = if (progress > 0.5f) text else null,
            tint = contentColor,
            modifier = Modifier
                .offset(x = iconStart)
                .size(iconSize),
        )
    }
}

/** The pill's width with [text]: the button's at morph progress 0. */
@Composable
internal fun rememberAddWallpaperPillWidth(text: String): Dp {
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, labelStyle, textMeasurer, density) {
        val labelWidth = with(density) {
            textMeasurer.measure(AnnotatedString(text), style = labelStyle, maxLines = 1).size.width.toDp()
        }
        PillStartPadding + PillIconSize + PillIconTextGap + labelWidth + PillEndPadding
    }
}

internal object AddWallpaperButtonDefaults {
    val IconSize: Dp = 24.dp
    private val ContentPadding: Dp = 8.dp

    /** The round button's size; the pill is as high. */
    val Size: Dp = IconSize + ContentPadding * 2

    /** The button's width at [morphProgress]: from the pill's [pillWidth] to the round button's [Size]. */
    fun width(pillWidth: Dp, morphProgress: Float): Dp = lerp(pillWidth, Size, morphProgress.coerceIn(0f, 1f))
}

private val PillStartPadding = 16.dp
private val PillEndPadding = 20.dp
private val PillIconSize = 18.dp
private val PillIconTextGap = 8.dp

/** How far the label travels to the right while the button turns round. */
private val LabelExitDistance = 24.dp

private const val POP_SCALE = 0.7f
private const val POP_DOWN_MS = 90
private const val POP_UP_MS = 140

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun AddWallpaperButtonPillPreview() {
    AddWallpaperButton(text = "Add wallpaper", morphProgress = 0f, onClick = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun AddWallpaperButtonMorphPreview() {
    AddWallpaperButton(text = "Add wallpaper", morphProgress = 0.5f, onClick = {})
}

@Preview(showBackground = true, backgroundColor = 0xFF1C1B1F)
@Composable
private fun AddWallpaperButtonRoundPreview() {
    AddWallpaperButton(text = "Add wallpaper", morphProgress = 1f, onClick = {})
}
