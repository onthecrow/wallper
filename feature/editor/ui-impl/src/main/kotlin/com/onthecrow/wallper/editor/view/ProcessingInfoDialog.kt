package com.onthecrow.wallper.editor.view

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.editor.ui.R

/** Original and optimised, in plain words. OK and dismissing differ only for a switch that waits for it. */
@Composable
internal fun ProcessingInfoDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
        title = { Text(text = stringResource(R.string.editor_processing_info_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Mode(name = R.string.editor_original, description = R.string.editor_original_description)
                Mode(name = R.string.editor_optimised, description = R.string.editor_optimised_description)
            }
        },
    )
}

@Composable
private fun Mode(@StringRes name: Int, @StringRes description: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(name), style = MaterialTheme.typography.titleSmall)
        Text(text = stringResource(description))
    }
}

@Preview
@Composable
private fun ProcessingInfoDialogPreview() {
    ProcessingInfoDialog(onConfirm = {}, onDismiss = {})
}
