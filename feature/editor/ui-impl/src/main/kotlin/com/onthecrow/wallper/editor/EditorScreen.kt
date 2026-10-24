@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.onthecrow.wallper.editor.view.OptimisedToggle
import com.onthecrow.wallper.editor.view.ProcessingInfoDialog
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
                        PlaybackEffect(
                            player = player,
                            trim = state.trim,
                            play = state.isPlaying && !isDragging && !isExporting,
                            onEnd = { onEvent(EditorEvent.OnPlaybackEnded) },
                        )
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
                            initialCropRect = media.edited?.crop?.toRect(),
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
                            // Without a known length there is nothing to trim.
                            trim = state.trim.takeIf { durationMs > 0 },
                            isPlaying = state.isPlaying,
                            onPlayPauseClick = { onEvent(EditorEvent.OnPlayPauseClick) },
                            onSetTrimStartClick = { onEvent(EditorEvent.OnSetTrimStartClick(player.positionMs)) },
                            onSetTrimEndClick = { onEvent(EditorEvent.OnSetTrimEndClick(player.positionMs)) },
                            modifier = Modifier.padding(bottom = 12.dp),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Only a video is ever optimised: a picture is always kept as it is.
                    if (media?.type == MediaType.VIDEO) {
                        OptimisedToggle(
                            isOptimised = state.isOptimised,
                            onToggle = { onEvent(EditorEvent.OnOptimisedToggle) },
                            onInfoClick = { onEvent(EditorEvent.OnProcessingInfoClick) },
                        )
                    }
                    Button(onClick = { onEvent(EditorEvent.OnSaveClick(rect.value.toCropRect())) }) {
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
            if (state.processingInfo != null) {
                ProcessingInfoDialog(
                    onConfirm = { onEvent(EditorEvent.OnProcessingInfoConfirm) },
                    onDismiss = { onEvent(EditorEvent.OnProcessingInfoDismiss) },
                )
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

/**
 * Play / pause between the buttons that put the start and the end of [trim] at the playhead, above the trim
 * bar. A button is off while it would leave the kept part too short. Without a [trim] there are no such
 * buttons.
 */
@Composable
private fun PlaybackControls(
    player: EditorPlayer,
    trim: ClipRange?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onSetTrimStartClick: () -> Unit,
    onSetTrimEndClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Recomposes when a button turns on or off rather than every frame.
    val canSetStart by remember(player, trim) { derivedStateOf { trim?.withStartAt(player.positionMs) != null } }
    val canSetEnd by remember(player, trim) { derivedStateOf { trim?.withEndAt(player.positionMs) != null } }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trim != null) {
            SetTrimBoundaryButton(
                onClick = onSetTrimStartClick,
                enabled = canSetStart,
                contentDescription = stringResource(R.string.editor_set_trim_start),
            )
        }
        FilledIconButton(
            onClick = onPlayPauseClick,
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.5f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.5f))
                },
                label = "play / pause",
            ) { playing ->
                Icon(
                    painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = stringResource(if (playing) R.string.editor_pause else R.string.editor_play),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (trim != null) {
            // The same arrow turned around: the bar on the right, pointing back into the kept part.
            SetTrimBoundaryButton(
                onClick = onSetTrimEndClick,
                enabled = canSetEnd,
                contentDescription = stringResource(R.string.editor_set_trim_end),
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

/** Fades between its enabled and disabled colours instead of switching. */
@Composable
private fun SetTrimBoundaryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = IconButtonDefaults.filledTonalIconButtonColors()
    val containerColor by animateColorAsState(
        targetValue = if (enabled) colors.containerColor else colors.disabledContainerColor,
        label = "trim button container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) colors.contentColor else colors.disabledContentColor,
        label = "trim button content",
    )
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_trim_start),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
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

private fun CropRect.toRect() = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

@Preview
@Composable
private fun EditorScreenPreview() {
    EditorScreen(state = EditorState(), onEvent = {})
}
