package com.onthecrow.wallper.wallpapers.group

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
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
 *
 * Either shape has an outline of [outlineColor], the colour of what is behind it, so that over the panel it
 * looks cut into it.
 */
@Composable
internal fun AddWallpaperButton(
    text: String,
    morphProgress: Float,
    outlineColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = morphProgress.coerceIn(0f, 1f)
    val pop = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidth: Dp = remember(text, labelStyle, textMeasurer, density) {
        with(density) {
            textMeasurer.measure(AnnotatedString(text), style = labelStyle, maxLines = 1).size.width.toDp()
        }
    }

    val outlineWidth = AddWallpaperButtonDefaults.OutlineWidth
    val roundSize = AddWallpaperButtonDefaults.Size
    val pillWidth = PillStartPadding + PillIconSize + PillIconTextGap + labelWidth + PillEndPadding + outlineWidth * 2
    val width = lerp(pillWidth, roundSize, progress)
    val iconSize = lerp(PillIconSize, AddWallpaperButtonDefaults.IconSize, progress)
    val iconStart = lerp(PillStartPadding, (width - outlineWidth * 2 - iconSize) / 2, progress)
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
            .background(outlineColor, CircleShape)
            .padding(outlineWidth)
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

internal object AddWallpaperButtonDefaults {
    val OutlineWidth: Dp = 4.dp
    val IconSize: Dp = 24.dp
    private val ContentPadding: Dp = 8.dp

    /** The round button's size, its outline included; the pill is as high. */
    val Size: Dp = IconSize + ContentPadding * 2 + OutlineWidth * 2
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

@Preview(showBackground = true)
@Composable
private fun AddWallpaperButtonPillPreview() {
    AddWallpaperButton(text = "Add wallpaper", morphProgress = 0f, outlineColor = Color.White, onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun AddWallpaperButtonMorphPreview() {
    AddWallpaperButton(text = "Add wallpaper", morphProgress = 0.5f, outlineColor = Color.White, onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun AddWallpaperButtonRoundPreview() {
    AddWallpaperButton(text = "Add wallpaper", morphProgress = 1f, outlineColor = Color.White, onClick = {})
}
