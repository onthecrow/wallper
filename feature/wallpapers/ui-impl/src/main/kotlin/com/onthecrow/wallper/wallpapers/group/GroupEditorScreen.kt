@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers.group

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.onthecrow.wallper.core.designsystem.R as DesignR
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import com.onthecrow.wallper.wallpapers.view.WallpaperCard
import com.onthecrow.wallper.wallpapers.view.labelRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Makes or edits a group: the pick step (the list's cards, ticked ones tinted) and the arrange step (the
 * members in order, swiped aside to take them out, the time each shows, save).
 */
@Composable
internal fun GroupEditorScreen(
    state: GroupEditorState,
    eventFlow: Flow<GroupEditorEvent>,
    onEvent: (GroupEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val saveFailedMessage = stringResource(R.string.group_save_failed)
    LaunchedEffect(eventFlow) {
        eventFlow.collect { event ->
            if (event == GroupEditorEvent.OnSaveFailed) {
                Toast.makeText(context, saveFailedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Back from adding goes back to the members; otherwise it leaves, like the arrow.
    val backCancelsPick = state.step == GroupEditorStep.PICK && state.members.isNotEmpty()
    BackHandler(enabled = backCancelsPick) { onEvent(GroupEditorEvent.OnPickCancel) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when {
                                state.step == GroupEditorStep.PICK -> R.string.group_pick_title
                                state.isNew -> R.string.group_new_title
                                else -> R.string.group_edit_title
                            }
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            onEvent(if (backCancelsPick) GroupEditorEvent.OnPickCancel else GroupEditorEvent.OnCloseClick)
                        },
                    ) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.step == GroupEditorStep.ARRANGE) {
                        IconButton(onClick = { onEvent(GroupEditorEvent.OnAddClick) }) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_plus),
                                contentDescription = stringResource(R.string.group_add),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            when (state.step) {
                GroupEditorStep.PICK -> PickBar(
                    enabled = state.picked.isNotEmpty(),
                    onConfirm = { onEvent(GroupEditorEvent.OnPickConfirm) },
                )
                GroupEditorStep.ARRANGE -> ArrangeBar(
                    slideDuration = state.slideDuration,
                    memberCount = state.memberItems.size,
                    canSave = state.canSave,
                    onDurationPick = { onEvent(GroupEditorEvent.OnDurationPick(it)) },
                    onSave = { onEvent(GroupEditorEvent.OnSaveClick) },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.padding(padding),
            label = "group step",
        ) { step ->
            when (step) {
                GroupEditorStep.PICK -> PickGrid(
                    wallpapers = state.pickable,
                    picked = state.picked,
                    onClick = { onEvent(GroupEditorEvent.OnWallpaperClick(it)) },
                )
                GroupEditorStep.ARRANGE -> if (state.isLoaded) {
                    MemberList(
                        members = state.memberItems,
                        onRemove = { onEvent(GroupEditorEvent.OnMemberRemove(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickGrid(wallpapers: List<WallpaperItem>, picked: Set<Int>, onClick: (Int) -> Unit) {
    LazyVerticalGrid(
        contentPadding = PaddingValues(8.dp),
        columns = GridCells.Fixed(2),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(wallpapers, { it.id }) { wallpaper ->
            WallpaperCard(
                wallpaper = wallpaper,
                onClick = { onClick(wallpaper.id) },
                onEditClick = {},
                onDeleteClick = {},
                selected = wallpaper.id in picked,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun MemberList(members: List<WallpaperItem>, onRemove: (Int) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(R.string.group_swipe_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(members, { it.id }) { member ->
            val dismissState = rememberSwipeToDismissBoxState()
            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {},
                onDismiss = { onRemove(member.id) },
                modifier = Modifier.animateItem(),
            ) {
                MemberRow(member)
            }
        }
    }
}

/** A member: its picture, wide like a screen turned sideways, and what it is. */
@Composable
private fun MemberRow(member: WallpaperItem) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BuiltInPlaceholderColor),
                ) {
                    member.picturePath?.let { path ->
                        AsyncImage(
                            model = path,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            },
            headlineContent = {
                Text(text = stringResource(if (member.isPhoto) R.string.kind_photo else member.labelRes()))
            },
        )
    }
}

@Composable
private fun PickBar(enabled: Boolean, onConfirm: () -> Unit) {
    Button(
        onClick = onConfirm,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        Text(text = stringResource(android.R.string.ok))
    }
}

/** The time each member shows, then save; save waits for [GroupEditorState.MIN_MEMBERS]. */
@Composable
private fun ArrangeBar(
    slideDuration: SlideDuration,
    memberCount: Int,
    canSave: Boolean,
    onDurationPick: (SlideDuration) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.group_duration_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SlideDuration.CHOICES.forEach { choice ->
                FilterChip(
                    selected = choice == slideDuration,
                    onClick = { onDurationPick(choice) },
                    label = { Text(text = choice.label()) },
                )
            }
        }
        if (memberCount < GroupEditorState.MIN_MEMBERS) {
            Text(
                text = pluralStringResource(R.plurals.group_too_few, GroupEditorState.MIN_MEMBERS, GroupEditorState.MIN_MEMBERS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = stringResource(R.string.group_save))
        }
    }
}

@Composable
private fun SlideDuration.label(): String = when {
    isOnWake -> stringResource(R.string.group_duration_on_wake)
    ms % HOUR_MS == 0L -> stringResource(R.string.group_duration_hours, (ms / HOUR_MS).toInt())
    ms % MINUTE_MS == 0L -> stringResource(R.string.group_duration_minutes, (ms / MINUTE_MS).toInt())
    else -> stringResource(R.string.group_duration_seconds, (ms / SECOND_MS).toInt())
}

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/** Behind a picture that isn't there yet: a built-in's preview still rendering. */
private val BuiltInPlaceholderColor = Color(0xFF050507)

@Preview
@Composable
private fun GroupPickPreview() {
    GroupEditorScreen(
        state = GroupEditorState(
            wallpapers = listOf(
                WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE),
                WallpaperItem(2, null, true),
                WallpaperItem(3, null, false, isOptimised = true),
            ),
            picked = setOf(2),
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}

@Preview
@Composable
private fun GroupArrangePreview() {
    GroupEditorScreen(
        state = GroupEditorState(
            isNew = false,
            step = GroupEditorStep.ARRANGE,
            wallpapers = listOf(WallpaperItem(2, null, true), WallpaperItem(3, null, false, isOptimised = true)),
            members = listOf(3, 2),
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}
