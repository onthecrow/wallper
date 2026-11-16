package com.onthecrow.wallper.wallpapers.view

import androidx.annotation.StringRes
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.onthecrow.nimbus.NimbusEffect
import com.onthecrow.nimbus.NimbusLayer
import com.onthecrow.nimbus.NimbusMotion
import com.onthecrow.nimbus.nimbus
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import kotlin.math.roundToInt

/**
 * A wallpaper of the list; a tap makes it the active one, which glows round its edge ([activeGlow]). Over its
 * picture: its actions (top right) and what it is (bottom right).
 *
 * With [selected] it is a choice instead (a group's members): no actions and no glow; a tap toggles it,
 * and a chosen one is tinted in the accent colour with a check in the middle.
 *
 * @param onEditClick the editor for media that keeps its original, a group's members, the settings of a built-in.
 * @param onDeleteClick the user's media and groups only: built-ins stay.
 */
@Composable
internal fun WallpaperCard(
    wallpaper: WallpaperItem,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
) {
    val haptics = LocalHapticFeedback.current
    val isMarked = selected == null && wallpaper.isActive
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val accent = remember(primary) { primary.vivid() }
    val contrast = remember(tertiary) { tertiary.vivid() }
    val activeDescription = stringResource(R.string.active_wallpaper)
    Card(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        shape = CardShape,
        modifier = modifier
            // Over its neighbours, which its glow reaches.
            .zIndex(if (isMarked) 1f else 0f)
            .activeGlow(isMarked, accent, contrast)
            .semantics { if (isMarked) stateDescription = activeDescription },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            WallpaperPicture(wallpaper, Modifier.matchParentSize())
            if (selected != null) {
                SelectionMark(selected)
            } else {
                // Each element shades its corner behind it. The actions' shade reaches furthest: they come
                // first, so that it falls on the picture only.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .cornerShade(Alignment.TopEnd),
                ) {
                    EditButton(wallpaper, onEditClick)
                    if (wallpaper.builtIn == null) {
                        IconButton(onClick = onDeleteClick, colors = IconButtonDefaults.iconButtonColors(contentColor = ActionColor)) {
                            Icon(painter = painterResource(DesignR.drawable.ic_delete), contentDescription = stringResource(DesignR.string.delete))
                        }
                    }
                }
            }
            KindLabel(wallpaper)
        }
    }
}

/**
 * The pencil of a wallpaper: its editor, or a built-in's settings. An optimised video keeps no original to edit:
 * it has none.
 */
@Composable
private fun EditButton(wallpaper: WallpaperItem, onClick: () -> Unit) {
    if (wallpaper.isOptimised) return
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = ActionColor)) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_edit),
            contentDescription = stringResource(if (wallpaper.builtIn != null) R.string.settings else DesignR.string.edit),
        )
    }
}

/** The card's pencil alone, top right over its corner's shade: a series' member row. */
@Composable
internal fun BoxScope.CornerEditButton(wallpaper: WallpaperItem, onClick: () -> Unit) {
    if (wallpaper.isOptimised) return
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .cornerShade(Alignment.TopEnd),
    ) {
        EditButton(wallpaper, onClick)
    }
}

/**
 * The wallpaper's picture, scaled to fill and cut evenly on the sides that don't fit. Until a built-in's
 * preview or a series' picture is made there is no picture, only the colour behind it.
 */
@Composable
internal fun WallpaperPicture(wallpaper: WallpaperItem, modifier: Modifier = Modifier) {
    Box(modifier = modifier.then(if (wallpaper.isPictureMade) Modifier.background(BuiltInPlaceholderColor) else Modifier)) {
        wallpaper.picturePath?.let { picturePath ->
            AsyncImage(
                modifier = Modifier.fillMaxSize(),
                model = picturePath,
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
        }
    }
}

/** What the wallpaper is ([labelRes]), bottom right of a card or a row, over the corner's shade. */
@Composable
internal fun BoxScope.KindLabel(wallpaper: WallpaperItem) {
    WallpaperLabel(
        text = stringResource(wallpaper.labelRes()),
        isOutlined = wallpaper.isOptimised,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .cornerShade(Alignment.BottomEnd)
            .padding(8.dp),
    )
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

/** A chosen card: the accent colour over the picture and a check in the middle; both fade in and out. */
@Composable
private fun BoxScope.SelectionMark(selected: Boolean) {
    val alpha by animateFloatAsState(targetValue = if (selected) 1f else 0f, label = "selection")
    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = SelectionTintAlpha)),
    )
    Icon(
        painter = painterResource(R.drawable.ic_check),
        contentDescription = if (selected) stringResource(R.string.group_picked) else null,
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = 0.6f + 0.4f * alpha
                scaleY = 0.6f + 0.4f * alpha
            }
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(12.dp),
        tint = MaterialTheme.colorScheme.onPrimary,
    )
}

/**
 * Nimbus round the active card, as round the connect button of OnthecrowVPN: two layers of spikes turning
 * opposite ways, in the theme's accent and the colour that sets it off, its tertiary, both at full strength
 * ([vivid]: the theme's tones are pastel in the dark theme, and a glow in them is white). It follows the
 * card's shape and fills it, under the card. Nimbus needs API 26: older devices show no glow. Off, it isn't
 * drawn at all.
 */
private fun Modifier.activeGlow(isActive: Boolean, accent: Color, contrast: Color): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return this
    return nimbus(
        shape = CardShape,
        active = isActive,
        // Back to front: the tertiary behind, the accent over it.
        layers = listOf(
            NimbusLayer(
                color = contrast,
                minSpikesCount = 4,
                maxSpikesCount = 7,
                maxPeakHeight = 0.12f,
                alpha = 0.75f,
                motionMode = NimbusMotion.RIGID,
                rotationSpeed = -0.15f,
            ),
            NimbusLayer(
                color = accent,
                minSpikesCount = 5,
                maxSpikesCount = 9,
                maxPeakHeight = 0.08f,
                alpha = 0.8f,
                motionMode = NimbusMotion.RIGID,
                rotationSpeed = 0.2f,
            ),
        ),
        collar = 2.dp,
        effect = NimbusEffect.GLOW,
        activationSpec = tween(GlowMillis),
    )
}

/** The colour's hue at full strength, and light. */
private fun Color.vivid(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    return Color.hsv(hsv[0], hsv[1].coerceAtLeast(VividSaturation), hsv[2].coerceAtLeast(VividValue))
}

/** What the wallpaper is: a group, a built-in's kind, or how the user's media is kept, as it was or optimised. */
@StringRes
private fun WallpaperItem.labelRes(): Int = when {
    isGroup -> R.string.series
    builtIn == BuiltInWallpaper.BLACK_HOLE -> R.string.animation_3d
    isOptimised -> R.string.optimised
    else -> R.string.original
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
 * Material palette, muted: grey 400 for the actions, grey 100 for the label and grey 900 for the text on the
 * filled one.
 */
private val ActionColor = Color(0xFFBDBDBD)
private val LabelColor = Color(0xFFF5F5F5)
private val LabelOnFilledColor = Color(0xFF212121)

/** How long the glow takes to come or go when another wallpaper becomes the active one. */
private const val GlowMillis = 700

/** The card's shape, which the glow follows: Material's default for cards. */
private val CardShape = RoundedCornerShape(12.dp)

/** The least saturation and value of the glow's colours ([vivid]). */
private const val VividSaturation = 0.7f
private const val VividValue = 0.9f

/** How much of the accent colour covers a chosen card. */
private const val SelectionTintAlpha = 0.45f

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
private fun SelectedWallpaperCardPreview() {
    WallpaperCard(
        wallpaper = WallpaperItem(0, null, isActive = false),
        onClick = {},
        onEditClick = {},
        onDeleteClick = {},
        selected = true,
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
