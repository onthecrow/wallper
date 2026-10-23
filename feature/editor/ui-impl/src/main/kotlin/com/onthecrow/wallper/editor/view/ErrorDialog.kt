package com.onthecrow.wallper.editor.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.onthecrow.wallper.editor.domain.model.VideoExportException

/** The error in full; OK, back or a tap outside closes it. */
@Composable
internal fun ErrorDialog(error: Throwable, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    if (error is VideoExportException) {
                        item {
                            Text(
                                text = "Error code: ${error.errorCode}"
                            )
                        }
                    }
                    item {
                        Text(
                            text = "Error message: ${error.message}"
                        )
                    }
                    item {
                        Text(
                            text = "Error: ${error.stackTraceToString()}"
                        )
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(8.dp),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Preview
@Composable
private fun ErrorDialogPreview() {
    ErrorDialog(error = Throwable("Preview"), onDismiss = {})
}
