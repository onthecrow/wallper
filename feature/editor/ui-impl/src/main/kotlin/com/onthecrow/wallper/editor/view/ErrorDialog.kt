package com.onthecrow.wallper.editor.view

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onthecrow.wallper.editor.domain.model.VideoExportException

@Composable
internal fun ErrorDialog(error: Throwable) {
    Dialog(
        onDismissRequest = { },
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                item { Text(text = "Ярик пидарас") }
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
        }
    }
}

@Preview
@Composable
private fun ErrorDialogPreview() {
    ErrorDialog(error = Throwable("Preview"))
}