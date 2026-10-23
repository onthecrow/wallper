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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.window.layout.WindowMetricsCalculator
import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBar
import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBarDefaults
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.editor.cropper.ImageCropper
import com.onthecrow.wallper.editor.cropper.settings.CropDefaults
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.preview.EditorPlayer
import com.onthecrow.wallper.editor.preview.PlaybackEffect
import com.onthecrow.wallper.editor.preview.rememberEditorPlayer
import com.onthecrow.wallper.editor.preview.rememberVideoFrames
import com.onthecrow.wallper.editor.ui.R
import com.onthecrow.wallper.editor.view.ErrorDialog
import timber.log.Timber

@Composable
internal fun EditorScreen(
    state: EditorState,
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
                    // A picture has nothing to play or trim.
                    val player = if (media.type == MediaType.VIDEO) {
                        rememberEditorPlayer(media.path, onError = { onEvent(EditorEvent.OnPreviewFailed(it)) })
                    } else {
                        null
                    }
                    // A drag on the trim bar holds playback without touching the user's choice, and so does an
                    // export: it needs the decoders.
                    var isDragging by remember(player) { mutableStateOf(false) }
                    val isExporting = state.exportStatus is ExportStatus.InProgress
                    if (player != null) {
                        PlaybackEffect(player, state.trim, play = state.isPlaying && !isDragging && !isExporting)
                    }

                    // The preview shows the video rotated upright, so the crop works in those pixels too.
                    val videoSize = IntSize(media.mediaInfo.displayWidth, media.mediaInfo.displayHeight)
                    val videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                    Box(
                        modifier = Modifier
                            .padding(32.dp)
                            .weight(1f)
                            .aspectRatio(videoAspectRatio)
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
                            player = player,
                            videoSize = videoSize,
                            cropStyle = cropStyle,
                            cropProperties = cropProperties,
                            cropRect = { cropRect ->
                                Timber.d("cropRect: $cropRect")
                                rect.value = cropRect
                            },
                        )
                    }

                    if (player != null) {
                        val durationMs = media.mediaInfo.durationMs
                        PlaybackControls(
                            player = player,
                            durationMs = durationMs,
                            isPlaying = state.isPlaying,
                            onPlayPauseClick = { onEvent(EditorEvent.OnPlayPauseClick) },
                        )
                        // Without a known length there is nothing to trim: the export keeps the whole video.
                        if (durationMs > 0) {
                            val frames = rememberVideoFrames(media.path, durationMs, videoAspectRatio, paused = isExporting)
                            TrimBar(
                                durationMs = durationMs,
                                position = { player.positionMs },
                                startMs = state.trim.startMs,
                                endMs = state.trim.endMs,
                                onTrimChange = { startMs, endMs ->
                                    onEvent(EditorEvent.OnTrimChange(ClipRange(startMs, endMs)))
                                },
                                onSeek = player::seekTo,
                                onDragStart = {
                                    isDragging = true
                                    player.setScrubbing(true)
                                },
                                onDragEnd = {
                                    player.setScrubbing(false)
                                    isDragging = false
                                },
                                frames = { frames.value },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }

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
                is ExportStatus.Error -> ErrorDialog(
                    error = status.error,
                    onDismiss = { onEvent(EditorEvent.OnErrorDismiss) },
                )
                ExportStatus.Success, null -> Unit
            }
        }
    }
}

/** The time of the frame on screen, and the play / pause button in the middle, above the trim bar. */
@Composable
private fun PlaybackControls(
    player: EditorPlayer,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
) {
    // Recomposes once a second rather than every frame.
    val second by remember(player) { derivedStateOf { player.positionMs / 1_000 } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${TrimBarDefaults.formatTime(second * 1_000)} / ${TrimBarDefaults.formatTime(durationMs)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        IconButton(onClick = onPlayPauseClick) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (isPlaying) R.string.editor_pause else R.string.editor_play),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
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
    EditorScreen(state = EditorState(), onEvent = {})
}
