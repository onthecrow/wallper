package com.onthecrow.wallper.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.onthecrow.wallper.core.designsystem.R

/**
 * Deleting a wallpaper can't be undone: its files go with it. The list and the storage screen ask alike.
 * [text] says what goes; by default, the wallpaper's copy of the media.
 */
@Composable
fun DeleteWallpaperDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String = stringResource(R.string.delete_wallpaper_text),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.delete_wallpaper_title)) },
        text = { Text(text = text) },
    )
}

@Preview
@Composable
private fun DeleteWallpaperDialogPreview() {
    DeleteWallpaperDialog(onConfirm = {}, onDismiss = {})
}
