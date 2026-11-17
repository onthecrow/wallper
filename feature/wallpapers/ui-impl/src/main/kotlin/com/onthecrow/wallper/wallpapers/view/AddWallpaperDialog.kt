@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.wallpapers.ui.R
import kotlinx.coroutines.launch

/**
 * What the «+» adds: a new wallpaper from the gallery, or a series of the wallpapers there are. No title: the
 * «+» says what it is for, and each option says where it starts from. A series is dimmed until there are
 * wallpapers enough for one ([offersSeries]), and a press then says so instead of starting it.
 */
@Composable
internal fun AddWallpaperDialog(
    offersSeries: Boolean,
    onMediaClick: () -> Unit,
    onSeriesClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddOption(
                    icon = R.drawable.ic_add_photo,
                    title = stringResource(R.string.add_media),
                    description = stringResource(R.string.add_media_description),
                    onClick = onMediaClick,
                )
                SeriesOption(isAvailable = offersSeries, onClick = onSeriesClick)
            }
        },
    )
}

/**
 * «New series». Until there are two wallpapers it could pick it is dimmed, and a press shows why in a tooltip
 * under it, which goes by itself; TalkBack reads that as its state.
 */
@Composable
private fun SeriesOption(isAvailable: Boolean, onClick: () -> Unit) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.add_series_unavailable)
    val showWhy: () -> Unit = { scope.launch { tooltip.show() } }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip(caretShape = TooltipDefaults.caretShape()) { Text(text = unavailable) }
        },
        state = tooltip,
        // Only a press on the option shows it, not a long press or a hover.
        enableUserInput = false,
    ) {
        AddOption(
            icon = R.drawable.ic_series,
            title = stringResource(R.string.add_series),
            description = stringResource(R.string.add_series_description),
            onClick = if (isAvailable) onClick else showWhy,
            isAvailable = isAvailable,
            modifier = if (isAvailable) Modifier else Modifier.semantics { stateDescription = unavailable },
        )
    }
}

/** An option of the dialog; not [isAvailable], it is drawn as Material draws a disabled one, yet still takes a press. */
@Composable
private fun AddOption(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAvailable: Boolean = true,
) {
    val contentAlpha = if (isAvailable) 1f else DisabledContentAlpha
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            // The whole of the place a badge would take, in the accent: no badge behind it.
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                modifier = Modifier.size(40.dp),
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
    }
}

/** Material's disabled content: 38 % of its colour. */
private const val DisabledContentAlpha = 0.38f

@Preview
@Composable
private fun AddWallpaperDialogPreview() {
    AddWallpaperDialog(offersSeries = true, onMediaClick = {}, onSeriesClick = {}, onDismiss = {})
}

@Preview
@Composable
private fun AddWallpaperDialogWithoutSeriesPreview() {
    AddWallpaperDialog(offersSeries = false, onMediaClick = {}, onSeriesClick = {}, onDismiss = {})
}
