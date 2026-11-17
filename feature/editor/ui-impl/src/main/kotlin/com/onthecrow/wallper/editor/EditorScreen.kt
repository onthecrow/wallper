@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.designsystem.component.colorpicker.ColorPicker
import com.onthecrow.wallper.core.designsystem.component.colorpicker.ColorPickerDefaults
import com.onthecrow.wallper.core.designsystem.component.colorpicker.HsvColor
import com.onthecrow.wallper.core.designsystem.component.phoneframe.rememberScreenAspectRatio
import com.onthecrow.wallper.core.designsystem.component.trimbar.TrimBar
import com.onthecrow.wallper.core.designsystem.component.wallpaperTint
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.editor.crop.CropEditor
import com.onthecrow.wallper.editor.crop.CropEditorDefaults
import com.onthecrow.wallper.editor.crop.FramingState
import com.onthecrow.wallper.editor.crop.rememberFramingState
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.preview.EditorPlayer
import com.onthecrow.wallper.editor.preview.PictureImage
import com.onthecrow.wallper.editor.preview.PlaybackEffect
import com.onthecrow.wallper.editor.preview.VideoSurface
import com.onthecrow.wallper.editor.preview.rememberEditorPlayer
import com.onthecrow.wallper.editor.preview.rememberVideoFrames
import com.onthecrow.wallper.editor.ui.R
import com.onthecrow.wallper.editor.view.ErrorDialog
import com.onthecrow.wallper.editor.view.ExportProgressDialog
import com.onthecrow.wallper.editor.view.OptimisedToggle
import com.onthecrow.wallper.editor.view.ProcessingInfoDialog

@Composable
internal fun EditorScreen(
    state: EditorState,
    onEvent: (EditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenAspectRatio = rememberScreenAspectRatio()
    val scope = rememberCoroutineScope()

    // Back from the tint's picker is back to the trim bar and the save row, not out of the editor.
    BackHandler(enabled = state.isTintOpen) { onEvent(EditorEvent.OnTintClose) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.editor_new_title else R.string.editor_edit_title)) },
                navigationIcon = {
                    // As the system's back: the tint's picker closes first.
                    IconButton(onClick = { onEvent(if (state.isTintOpen) EditorEvent.OnTintClose else EditorEvent.OnCloseClick) }) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.editor_back))
                    }
                },
            )
        },
    ) { padding ->
        Box {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding(), top = padding.calculateTopPadding())
            val media = state.media
            // The preview shows the media upright, so the crops work in those pixels too.
            val cropState = media?.let {
                rememberFramingState(
                    mediaSize = IntSize(it.mediaInfo.displayWidth, it.mediaInfo.displayHeight),
                    screenAspectRatio = screenAspectRatio,
                    start = it.edited?.framing,
                )
            }

            if (media != null && cropState != null) {
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
                val durationMs = media.mediaInfo.durationMs

                // From the trim bar and the save row under the buttons, 0, to the tint's picker there, 1. Only
                // the layout reads it, while placing, so the way between them recomposes nothing.
                val tintProgress = animateFloatAsState(
                    targetValue = if (state.isTintOpen) 1f else 0f,
                    animationSpec = tween(TintPanelMotion.DURATION_MS, easing = FastOutSlowInEasing),
                    label = "tint panel",
                )
                // The picker is there only while any of it shows.
                val isTintPanelShown by remember { derivedStateOf { tintProgress.value > 0f } }
                val layoutDirection = LocalLayoutDirection.current

                EditorLayout(
                    tintProgress = { tintProgress.value },
                    cropScale = { full, visibleHeight ->
                        CropEditorDefaults.scaleToFit(this, layoutDirection, full, visibleHeight, screenAspectRatio)
                    },
                    modifier = contentModifier,
                    crop = {
                        CropEditor(
                            state = cropState,
                            onSnap = { onEvent(EditorEvent.OnCropSnapped) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            if (player != null) {
                                VideoSurface(player, Modifier.fillMaxSize())
                            } else {
                                PictureImage(media.path, Modifier.fillMaxSize())
                            }
                            // The whole picture through the tint, as the wallpaper shows it.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .wallpaperTint(state.tint.tint?.multiplierArgb)
                            )
                        }
                    },
                    controls = {
                        // The playback buttons in the middle; the tint and turn buttons share the room on their
                        // left evenly, the reset button sits on their right. A picture has only those: its
                        // tint and turn buttons share the left half.
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                TintButton(isOpen = state.isTintOpen, onClick = { onEvent(EditorEvent.OnTintClick) })
                                TurnButton(state = cropState)
                            }
                            if (player != null) {
                                PlaybackControls(
                                    player = player,
                                    // Without a known length there is nothing to trim.
                                    trim = state.trim.takeIf { durationMs > 0 },
                                    isPlaying = state.isPlaying,
                                    onPlayPauseClick = { onEvent(EditorEvent.OnPlayPauseClick) },
                                    onSetTrimStartClick = { onEvent(EditorEvent.OnSetTrimStartClick(player.positionMs)) },
                                    onSetTrimEndClick = { onEvent(EditorEvent.OnSetTrimEndClick(player.positionMs)) },
                                )
                            }
                            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                ResetButton(
                                    visible = state.hasEdits || cropState.isChanged,
                                    onClick = {
                                        cropState.reset(scope)
                                        onEvent(EditorEvent.OnResetClick)
                                    },
                                    modifier = Modifier.padding(end = 16.dp),
                                )
                            }
                        }
                    },
                    bottom = {
                        Column {
                            // Without a known length there is nothing to trim: the export keeps the whole video.
                            if (player != null && durationMs > 0) {
                                val videoAspectRatio = media.mediaInfo.displayWidth.toFloat() / media.mediaInfo.displayHeight
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
                            SaveRow(
                                // Only a video is ever optimised: a picture is always kept as it is.
                                isOptimised = state.isOptimised.takeIf { media.type == MediaType.VIDEO },
                                onEvent = onEvent,
                                onSaveClick = { onEvent(EditorEvent.OnSaveClick(cropState.framing())) },
                            )
                        }
                    },
                    tintPanel = {
                        if (state.isTintOpen || isTintPanelShown) {
                            TintPanel(
                                tint = state.tint,
                                onColorChange = { onEvent(EditorEvent.OnTintColorChange(it)) },
                                onToggle = { onEvent(EditorEvent.OnTintToggle) },
                            )
                        }
                    },
                )
            } else {
                // Nothing to save until the media is there.
                Box(contentModifier, contentAlignment = Alignment.BottomCenter) {
                    SaveRow(isOptimised = null, onEvent = onEvent, onSaveClick = {})
                }
            }
            if (state.processingInfo != null) {
                ProcessingInfoDialog(
                    onConfirm = { onEvent(EditorEvent.OnProcessingInfoConfirm) },
                    onDismiss = { onEvent(EditorEvent.OnProcessingInfoDismiss) },
                )
            }
            when (val status = state.exportStatus) {
                is ExportStatus.InProgress -> ExportProgressDialog(
                    status = status,
                    onCancel = { onEvent(EditorEvent.OnExportCancel) },
                )
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
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

/**
 * The editor from top to bottom: the [crop], the [controls], and under them the trim bar with the save row
 * ([bottom]) or the tint's picker ([tintPanel]), [tintProgress] of the way from the first to the second: the
 * one sinks and fades as the other rises from under the bottom edge, and back ([TintPanelMotion]). The progress
 * is read only while placing, so a step of the way neither recomposes nor measures anything.
 *
 * The crop is always laid out for the room it has over [bottom]. When the picker leaves it less, it is drawn
 * smaller as a whole, by [cropScale], in the middle of the room that is left: nothing inside it is laid out
 * again, so its frame keeps its proportions, and the picture, the gestures and the crop they give stay exactly
 * what they were. [bottom] stays in the composition while the picker covers it, so its height is always known.
 *
 * @param cropScale the scale for the crop laid out at `full` pixels to fit `visibleHeight` of them.
 */
@Composable
private fun EditorLayout(
    tintProgress: () -> Float,
    cropScale: Density.(full: IntSize, visibleHeight: Int) -> Float,
    crop: @Composable () -> Unit,
    controls: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
    tintPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(crop, controls, bottom, tintPanel),
        // The picker comes up from under the bottom edge.
        modifier = modifier.clipToBounds(),
    ) { (cropMeasurables, controlsMeasurables, bottomMeasurables, tintMeasurables), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val wide = Constraints.fixedWidth(width)
        val controlsPlaceables = controlsMeasurables.map { it.measure(wide) }
        val bottomPlaceables = bottomMeasurables.map { it.measure(wide) }
        val tintPlaceables = tintMeasurables.map { it.measure(wide) }
        val controlsHeight = controlsPlaceables.maxOfOrNull { it.height } ?: 0
        val bottomHeight = bottomPlaceables.maxOfOrNull { it.height } ?: 0
        val tintHeight = tintPlaceables.maxOfOrNull { it.height }

        val fullHeight = TintPanelMotion.fullCropHeight(height, controlsHeight, bottomHeight)
        val cropPlaceables = cropMeasurables.map { it.measure(Constraints.fixed(width, fullHeight)) }
        val density: Density = this

        layout(width, height) {
            // The progress is read here, so each step of the way only places. Without a picker to show there
            // is no way to go.
            val motion = TintPanelMotion(
                height = height,
                controlsHeight = controlsHeight,
                bottomHeight = bottomHeight,
                tintHeight = tintHeight ?: bottomHeight,
                progress = if (tintHeight == null) 0f else tintProgress(),
            )
            val visibleHeight = motion.cropHeight
            val scale = if (visibleHeight >= fullHeight) {
                1f
            } else {
                density.cropScale(IntSize(width, fullHeight), visibleHeight)
            }
            // Its middle in the middle of the room it has; a layer is scaled about its middle.
            cropPlaceables.forEach { placeable ->
                placeable.placeWithLayer(0, (visibleHeight - fullHeight) / 2) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            controlsPlaceables.forEach { it.place(0, motion.controlsTop) }
            // What is unseen isn't placed, so it takes no touches either.
            val bottomAlpha = motion.bottomAlpha
            val tintAlpha = motion.tintAlpha
            if (bottomAlpha > 0f) {
                bottomPlaceables.forEach { it.placeWithLayer(0, motion.bottomTop) { alpha = bottomAlpha } }
            }
            if (tintAlpha > 0f) {
                tintPlaceables.forEach { it.placeWithLayer(0, motion.tintTop) { alpha = tintAlpha } }
            }
        }
    }
}

/**
 * Original / optimised and save, under the trim bar. Without [isOptimised] there is no such choice: a picture
 * is always kept as it is.
 */
@Composable
private fun SaveRow(
    isOptimised: Boolean?,
    onEvent: (EditorEvent) -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isOptimised != null) {
            OptimisedToggle(
                isOptimised = isOptimised,
                onToggle = { onEvent(EditorEvent.OnOptimisedToggle) },
                onInfoClick = { onEvent(EditorEvent.OnProcessingInfoClick) },
            )
        }
        Button(onClick = onSaveClick) {
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

/**
 * Opens the tint's picker in place of what is under the buttons, and closes it again. A plain icon button like
 * the reset button across from it, without a container either way: only the icon's colour says the picker is
 * open.
 */
@Composable
private fun TintButton(isOpen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val iconColor by animateColorAsState(
        targetValue = if (isOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        label = "tint button",
    )
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics { selected = isOpen },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_tint),
            contentDescription = stringResource(R.string.editor_tint),
            tint = iconColor,
        )
    }
}

/**
 * Turns the crop's phone to the other way the screen can be turned, and back. A plain icon button like the tint
 * button beside it, in the accent while turned; a dot at the icon's corner says the turned screen has a crop of
 * its own, not one derived from the other.
 */
@Composable
private fun TurnButton(state: FramingState, modifier: Modifier = Modifier) {
    val iconColor by animateColorAsState(
        targetValue = if (state.isTurned) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        label = "turn button",
    )
    val hasOwnTurned = state.hasOwnTurned
    val ownDescription = stringResource(R.string.editor_turned_own_crop)
    IconButton(
        onClick = state::turn,
        modifier = modifier.semantics {
            selected = state.isTurned
            if (hasOwnTurned) stateDescription = ownDescription
        },
    ) {
        Box {
            Icon(
                painter = painterResource(R.drawable.ic_rotate_screen),
                contentDescription = stringResource(R.string.editor_turn_screen),
                tint = iconColor,
            )
            if (hasOwnTurned) {
                Canvas(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(TurnDotSize)
                ) { drawCircle(iconColor) }
            }
        }
    }
}

/** The turn button's dot: small, at the very corner of the icon. */
private val TurnDotSize = 4.dp

/**
 * The tint's colour and its switch, whose row says «Tint enabled» or «Tint disabled»: what the tint button puts
 * in place of the trim bar and the save row. The picker's card is as far from the screen's edges as the crop's
 * frame may come.
 */
@Composable
private fun TintPanel(
    tint: TintChoice,
    onColorChange: (HsvColor) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        ColorPicker(
            color = tint.color,
            onColorChange = onColorChange,
            modifier = Modifier.padding(horizontal = TintPanelPadding - ColorPickerDefaults.EdgeInset),
        )
        // The row toggles as a whole, like the settings' switches, and says which way it is.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = tint.isOn, role = Role.Switch, onValueChange = { onToggle() })
                .padding(horizontal = TintPanelPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // One fades into the other, like the label of original / optimised.
            AnimatedContent(
                targetState = tint.isOn,
                modifier = Modifier.weight(1f),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.CenterStart,
                label = "tint switch label",
            ) { isOn ->
                Text(
                    text = stringResource(if (isOn) R.string.editor_tint_enabled else R.string.editor_tint_disabled),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Switch(checked = tint.isOn, onCheckedChange = null)
        }
    }
}

/** The same as the crop frame's ([com.onthecrow.wallper.editor.crop.CropEditorDefaults.FramePadding]). */
private val TintPanelPadding = 24.dp

/** Undoes every change: crop, trim, tint and the original / optimised choice. Appears once there is one. */
@Composable
private fun ResetButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.5f),
        exit = fadeOut() + scaleOut(targetScale = 0.5f),
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_reset),
                contentDescription = stringResource(R.string.editor_reset),
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


@Preview
@Composable
private fun EditorScreenPreview() {
    EditorScreen(state = EditorState(), onEvent = {})
}
