package com.onthecrow.wallper.wallpapers.view

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import coil.compose.AsyncImage
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import kotlin.math.roundToInt

/**
 * A wallpaper of the list; a tap makes it the active one. Over its picture: whether it is active (top
 * left), its actions (top right) and what it is (bottom right).
 *
 * @param onEditClick the editor for media that keeps its original, the settings of a built-in.
 * @param onDeleteClick the user's media only: built-ins stay.
 */
@Composable
internal fun WallpaperCard(
    wallpaper: WallpaperItem,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Card(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (wallpaper.builtIn != null) Modifier.background(BuiltInPlaceholderColor) else Modifier),
        ) {
            // No picture while a built-in's preview is rendering: the placeholder background shows instead.
            wallpaper.picturePath?.let { picturePath ->
                AsyncImage(
                    modifier = Modifier.fillMaxSize(),
                    model = picturePath,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                )
            }
            // Each element shades its corner behind it. The actions' shade reaches furthest: they come
            // first, so that it falls on the picture only.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .cornerShade(Alignment.TopEnd),
            ) {
                val iconColors = IconButtonDefaults.iconButtonColors(contentColor = ActionColor)
                // An optimised video keeps no original to edit.
                if (!wallpaper.isOptimised) {
                    IconButton(onClick = onEditClick, colors = iconColors) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_edit),
                            contentDescription = stringResource(if (wallpaper.builtIn != null) R.string.settings else DesignR.string.edit),
                        )
                    }
                }
                if (wallpaper.builtIn == null) {
                    IconButton(onClick = onDeleteClick, colors = iconColors) {
                        Icon(painter = painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(DesignR.string.delete))
                    }
                }
            }
            ActiveIndicator(
                isActive = wallpaper.isActive,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .cornerShade(Alignment.TopStart)
                    .padding(12.dp),
            )
            WallpaperLabel(
                text = stringResource(wallpaper.labelRes()),
                isOutlined = wallpaper.isOptimised,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .cornerShade(Alignment.BottomEnd)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Shades the card's corner behind an element aligned there with [alignment], so that light content stays
 * readable on a bright picture: an elliptic gradient from the corner, [ShadeSpread] times the element's
 * size with its padding.
 */
private fun Modifier.cornerShade(alignment: Alignment): Modifier = drawBehind {
    val corner = alignment.align(IntSize.Zero, IntSize(size.width.roundToInt(), size.height.roundToInt()), layoutDirection)
        .toOffset()
    val radiusX = size.width * ShadeSpread
    val radiusY = size.height * ShadeSpread
    // A circle squeezed into an ellipse: radial gradients are circles.
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = corner) {
        drawCircle(
            brush = Brush.radialGradient(*ShadeStops, center = corner, radius = radiusX),
            radius = radiusX,
            center = corner,
        )
    }
}

@Composable
private fun ActiveIndicator(isActive: Boolean, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        targetValue = if (isActive) ActiveColor else InactiveColor,
        label = "active indicator",
    )
    Icon(
        painter = painterResource(if (isActive) R.drawable.ic_radio_checked else R.drawable.ic_radio_unchecked),
        contentDescription = if (isActive) stringResource(R.string.active_wallpaper) else null,
        modifier = modifier.size(24.dp),
        tint = color,
    )
}

/** What the wallpaper is: a built-in's kind, or how the user's media is kept, as it was or optimised. */
@StringRes
private fun WallpaperItem.labelRes(): Int = when (builtIn) {
    BuiltInWallpaper.BLACK_HOLE -> R.string.animation_3d
    null -> if (isOptimised) R.string.optimised else R.string.original
}

/**
 * A pill like a Material chip, smaller than Material's chips go (20 dp high): filled, or [isOutlined] for
 * an optimised video. Only a label: taps reach the card.
 */
@Composable
private fun WallpaperLabel(text: String, isOutlined: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .then(
                if (isOutlined) {
                    Modifier.border(1.dp, LabelColor, CircleShape)
                } else {
                    Modifier.background(LabelColor, CircleShape)
                }
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = if (isOutlined) LabelColor else LabelOnFilledColor,
        style = MaterialTheme.typography.labelSmall,
    )
}

/** Shown until a built-in's preview is rendered. */
private val BuiltInPlaceholderColor = Color(0xFF050507)

/**
 * Material palette, muted: green 300 for the active wallpaper, grey 400 for the others and the actions,
 * grey 100 for the label and grey 900 for the text on the filled one.
 */
private val ActiveColor = Color(0xFF81C784)
private val InactiveColor = Color(0xFFBDBDBD)
private val ActionColor = InactiveColor
private val LabelColor = Color(0xFFF5F5F5)
private val LabelOnFilledColor = Color(0xFF212121)

/** How far a corner's shade reaches, in sizes of the element there, and how it fades. */
private const val ShadeSpread = 2f
private val ShadeStops = arrayOf(
    0f to Color.Black.copy(alpha = 0.85f),
    0.6f to Color.Black.copy(alpha = 0.55f),
    1f to Color.Transparent,
)

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun WallpaperCardPreview() {
    WallpaperCard(
        wallpaper = WallpaperItem(0, null, isActive = true),
        onClick = {},
        onEditClick = {},
        onDeleteClick = {},
    )
}

@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun BuiltInWallpaperCardPreview() {
    WallpaperCard(
        wallpaper = WallpaperItem(0, null, isActive = false, builtIn = BuiltInWallpaper.BLACK_HOLE),
        onClick = {},
        onEditClick = {},
        onDeleteClick = {},
    )
}
