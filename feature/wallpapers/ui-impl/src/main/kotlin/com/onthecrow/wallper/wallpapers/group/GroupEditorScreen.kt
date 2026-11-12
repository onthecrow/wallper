@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers.group

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import com.onthecrow.wallper.wallpapers.view.KindLabel
import com.onthecrow.wallper.wallpapers.view.WallpaperCard
import com.onthecrow.wallper.wallpapers.view.WallpaperPicture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.roundToInt

/**
 * Makes or edits a series: the pick step (the list's cards, ticked ones tinted) and the arrange step (the
 * members in order, swiped aside to take them out; on a panel below them, the time each shows and save).
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
            )
        },
        bottomBar = {
            if (state.step == GroupEditorStep.PICK) {
                PickBar(
                    enabled = state.picked.isNotEmpty(),
                    onConfirm = { onEvent(GroupEditorEvent.OnPickConfirm) },
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
                GroupEditorStep.ARRANGE -> ArrangeStep(state = state, onEvent = onEvent)
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

/**
 * The members above the panel. «Add wallpaper» keeps its place at the end of the list; while that place is
 * off the panel it is a pill there, and once scrolling takes the place down to the panel it stays on the
 * panel's top edge as a round plus ([AddButtonPlacement]).
 *
 * The overscroll stretch is drawn by a layer that holds the list, the panel and the button, so that the
 * button never comes apart from the list.
 */
@Composable
private fun ArrangeStep(state: GroupEditorState, onEvent: (GroupEditorEvent) -> Unit) {
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val overscrollEffect = rememberOverscrollEffect()
    var bodyHeight by remember { mutableIntStateOf(0) }
    var panelAreaHeight by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The button may reach over the panel, never into the app bar.
            .clipToBounds()
            .onSizeChanged { bodyHeight = it.height }
            .overscroll(overscrollEffect),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = with(density) { panelAreaHeight.toDp() }),
        ) {
            if (state.isLoaded) {
                MemberList(
                    members = state.memberItems,
                    listState = listState,
                    overscrollEffect = overscrollEffect?.withoutVisualEffect(),
                    onRemove = { onEvent(GroupEditorEvent.OnMemberRemove(it)) },
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(ListFadeHeight)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
            )
        }
        ArrangePanel(
            slideDuration = state.slideDuration,
            memberCount = state.memberItems.size,
            canSave = state.canSave,
            onDurationPick = { onEvent(GroupEditorEvent.OnDurationPick(it)) },
            onSave = { onEvent(GroupEditorEvent.OnSaveClick) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { panelAreaHeight = it.height }
                .padding(PanelMargin),
        )
        if (state.isLoaded && bodyHeight > 0 && panelAreaHeight > 0) {
            val addButton = rememberAddButtonPlacement(listState, listBottom = (bodyHeight - panelAreaHeight).toFloat())
            AddWallpaperButton(
                text = stringResource(R.string.add_wallpaper),
                morphProgress = addButton.morphProgress,
                outlineColor = MaterialTheme.colorScheme.background,
                onClick = { onEvent(GroupEditorEvent.OnAddClick) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(x = 0, y = addButton.top().roundToInt()) },
            )
        }
    }
}

@Composable
private fun MemberList(
    members: List<WallpaperItem>,
    listState: LazyListState,
    overscrollEffect: OverscrollEffect?,
    onRemove: (Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        overscrollEffect = overscrollEffect,
        contentPadding = PaddingValues(top = ListTopPadding),
        verticalArrangement = Arrangement.spacedBy(MemberSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                text = stringResource(R.string.group_swipe_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
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
        // The button is drawn over the list (AddButtonPlacement); this keeps its place, so that at the end of
        // the list the whole of it is above the panel.
        item(key = ADD_BUTTON_KEY) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AddButtonPlaceTop + AddWallpaperButtonDefaults.Size),
            )
        }
    }
}

/** A member: its picture across the whole row, the middle of it where it is taller, and what it is. */
@Composable
private fun MemberRow(member: WallpaperItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(MemberHeight)
            .padding(horizontal = 16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            WallpaperPicture(member, Modifier.matchParentSize())
            KindLabel(member)
        }
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
private fun ArrangePanel(
    slideDuration: SlideDuration,
    memberCount: Int,
    canSave: Boolean,
    onDurationPick: (SlideDuration) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(top = 20.dp, bottom = 16.dp)) {
            Text(
                text = stringResource(R.string.group_duration_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp),
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
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp),
            ) {
                Text(text = stringResource(R.string.group_save))
            }
        }
    }
}

/**
 * Where «Add wallpaper» is. It follows its place at the end of the list: with the list scrolled to its end
 * that is right above the panel. Scrolling back from the end takes the place down until the button would
 * reach over the panel; from there it stays on the panel («pinned») as a round plus. Scrolling to the end
 * again, the list picks the button up and it turns back into the pill.
 */
@Stable
private class AddButtonPlacement(
    private val listState: LazyListState,
    /** From the top of the button's place in the list to the button. */
    private val gap: Float,
    /** The button's top when it is pinned to the panel. */
    private val pinnedTop: Float,
    private val pinThreshold: Float,
) {
    /** The button's top at its place in the list, or null when that place is below what the list shows. */
    private fun listTopOrNull(): Float? {
        val layoutInfo = listState.layoutInfo
        // Item offsets start after the top content padding, which the viewport's start offset is minus.
        return layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == ADD_BUTTON_KEY }
            ?.let { item -> item.offset - layoutInfo.viewportStartOffset + gap }
    }

    val isPinned: Boolean by derivedStateOf {
        listTopOrNull()?.let { top -> top > pinnedTop + pinThreshold } ?: true
    }

    /** Read while placing: it follows every scroll without recomposing. */
    fun top(): Float = if (isPinned) pinnedTop else listTopOrNull() ?: pinnedTop

    var morphProgress: Float by mutableFloatStateOf(0f)
}

/** @param listBottom the bottom of the list's viewport, from the top of the step. */
@Composable
private fun rememberAddButtonPlacement(listState: LazyListState, listBottom: Float): AddButtonPlacement {
    val density = LocalDensity.current
    val placement = remember(listState, listBottom, density) {
        with(density) {
            AddButtonPlacement(
                listState = listState,
                gap = AddButtonPlaceTop.toPx(),
                pinnedTop = listBottom - (AddWallpaperButtonDefaults.Size - AddButtonPanelOverlap - PanelMargin).toPx(),
                pinThreshold = 1.dp.toPx(),
            )
        }
    }
    placement.morphProgress = animateFloatAsState(
        targetValue = if (placement.isPinned) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "add wallpaper morph",
    ).value
    return placement
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

private const val ADD_BUTTON_KEY = "add wallpaper"

private val ListTopPadding = 8.dp
private val MemberHeight = 120.dp
private val MemberSpacing = 8.dp

/** Between the last member and «Add wallpaper». */
private val AddButtonGap = 16.dp

/** From the top of the button's place in the list to the button: the list's spacing makes up the rest of the gap. */
private val AddButtonPlaceTop = AddButtonGap - MemberSpacing

/** How far the pinned button reaches over the panel's top edge. */
private val AddButtonPanelOverlap = 12.dp

/** Around the panel: the list ends this far above it, and the button reaches over it from above. */
private val PanelMargin = 16.dp
private val PanelShape = RoundedCornerShape(28.dp)

/** The list fades out above the panel. */
private val ListFadeHeight = 16.dp

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
            wallpapers = listOf(
                WallpaperItem(1, null, false, BuiltInWallpaper.BLACK_HOLE),
                WallpaperItem(2, null, true),
                WallpaperItem(3, null, false, isOptimised = true),
            ),
            members = listOf(3, 1, 2),
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}
