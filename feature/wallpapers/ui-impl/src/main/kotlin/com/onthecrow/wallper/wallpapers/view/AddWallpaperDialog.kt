package com.onthecrow.wallper.wallpapers.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.wallpapers.ui.R

/** What the «+» adds: one wallpaper from the gallery, or a slideshow of the wallpapers there are. */
@Composable
internal fun AddWallpaperDialog(
    onMediaClick: () -> Unit,
    onSlideshowClick: () -> Unit,
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
        title = { Text(text = stringResource(R.string.add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AddOption(
                    title = stringResource(R.string.add_media),
                    description = stringResource(R.string.add_media_description),
                    onClick = onMediaClick,
                )
                AddOption(
                    title = stringResource(R.string.slideshow),
                    description = stringResource(R.string.add_slideshow_description),
                    onClick = onSlideshowClick,
                )
            }
        },
    )
}

@Composable
private fun AddOption(title: String, description: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun AddWallpaperDialogPreview() {
    AddWallpaperDialog(onMediaClick = {}, onSlideshowClick = {}, onDismiss = {})
}
