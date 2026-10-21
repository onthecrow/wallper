@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.layout.WindowMetricsCalculator
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.editor.cropper.ImageCropper
import com.onthecrow.wallper.editor.cropper.settings.CropDefaults
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.ui.R
import com.onthecrow.wallper.editor.view.ErrorDialog
import timber.log.Timber

/**
 * @param playerState the same state sampled less often: seeking the preview player on every
 * slider frame is too expensive.
 */
@Composable
internal fun EditorScreen(
    state: EditorState,
    playerState: EditorState,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenAspectRatio = rememberScreenAspectRatio()

    Scaffold(modifier = modifier) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = it.calculateBottomPadding(),
                        top = it.calculateTopPadding(),
                    ),
                verticalArrangement = Arrangement.Bottom,
            ) {
                val rect = remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
                val media = state.media

                if (media != null) {
                    val videoSize = IntSize(media.mediaInfo.width, media.mediaInfo.height)
                    Box(
                        modifier = Modifier
                            .padding(32.dp)
                            .weight(1f)
                            .aspectRatio(videoSize.width.toFloat() / videoSize.height.toFloat())
                            .align(Alignment.CenterHorizontally)
                    ) {

                        val handleSize: Float = LocalDensity.current.run { 20.dp.toPx() }

                        val cropProperties by remember {
                            mutableStateOf(
                                CropDefaults.properties(
                                    overlayRatio = 1f,
                                    aspectRatio = screenAspectRatio,
                                    fixedAspectRatio = true,
                                    handleSize = handleSize
                                )
                            )
                        }
                        val cropStyle by remember { mutableStateOf(CropDefaults.style()) }

                        ImageCropper(
                            modifier = Modifier
                                .fillMaxSize(),
                            videoUri = media.path,
                            videoSize = videoSize,
                            videoRange = state.timeLineRange,
                            uiSeek = state.seekPosition,
                            videoSeek = playerState.seekPosition,
                            cropStyle = cropStyle,
                            cropProperties = cropProperties,
                            cropRect = { cropRect ->
                                Timber.d("cropRect: $cropRect")
                                rect.value = cropRect
                            },
                        )
                    }
                }

                RangeSlider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    value = state.timeLineRange,
                    onValueChangeFinished = { onEvent(EditorEvent.OnTimeLineRangeChangeFinished) },
                    onValueChange = { onEvent(EditorEvent.OnTimeLineRangeChange(it)) },
                )

                Row(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = state.isAdditionalProcessing,
                        onCheckedChange = { onEvent(EditorEvent.OnAdditionalProcessingToggle) }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Additional processing")
                    IconButton(onClick = { onEvent(EditorEvent.OnAdditionalProcessingInfoClick) }) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_question_mark),
                            contentDescription = "",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, bottom = 16.dp, top = 8.dp)
                ) {
                    Button(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        onClick = { onEvent(EditorEvent.OnSaveClick(rect.value.toCropRect())) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_save_outline),
                                contentDescription = "",
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = "SAVE")
                        }
                    }
                }

            }
            when (val status = state.exportStatus) {
                is ExportStatus.InProgress -> ExportProgressDialog(status)
                is ExportStatus.Error -> ErrorDialog(error = status.error)
                ExportStatus.Success, null -> Unit
            }
        }
    }
}

@Composable
private fun ExportProgressDialog(status: ExportStatus.InProgress) {
    Dialog(
        onDismissRequest = { },
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            item {
                LinearProgressIndicator(
                    progress = { status.progress.toFloat() / 100f },
                )
            }
        }
    }
}

/** The crop frame keeps the proportions of the screen the wallpaper is drawn on. */
@Composable
private fun rememberScreenAspectRatio(): Float {
    val context = LocalContext.current
    return remember(context) {
        val bounds = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(context).bounds
        bounds.width().toFloat() / bounds.height().toFloat()
    }
}

private fun Rect.toCropRect() = CropRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

@Preview
@Composable
private fun EditorScreenPreview() {
    EditorScreen(state = EditorState(), playerState = EditorState(), onEvent = {})
}
