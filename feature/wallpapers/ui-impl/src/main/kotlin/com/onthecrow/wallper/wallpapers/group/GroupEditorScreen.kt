@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.wallpapers.group

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.withoutVisualEffect
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.onthecrow.wallper.core.designsystem.component.FAB
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.model.SlideDuration
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import com.onthecrow.wallper.wallpapers.ui.R
import com.onthecrow.wallper.wallpapers.view.KindLabel
import com.onthecrow.wallper.wallpapers.view.WallpaperCard
import com.onthecrow.wallper.wallpapers.view.WallpaperPicture
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow

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

    // Out here, not in the arrange step: that leaves the composition while more are picked, and coming back
    // the members are where they were.
    val memberListState = rememberLazyListState()

    // Back from adding goes back to the members; otherwise it leaves, like the arrow.
    val backCancelsPick = state.step == GroupEditorStep.PICK && state.members.isNotEmpty()
    BackHandler(enabled = backCancelsPick) { onEvent(GroupEditorEvent.OnPickCancel) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
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
        floatingActionButton = {
            // Comes up from below once something is ticked, and goes back down when nothing is.
            AnimatedVisibility(
                visible = state.step == GroupEditorStep.PICK && state.picked.isNotEmpty(),
                enter = slideInVertically { height -> height * FAB_TRAVEL },
                exit = slideOutVertically { height -> height * FAB_TRAVEL },
            ) {
                FAB(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(android.R.string.ok),
                    onClick = { onEvent(GroupEditorEvent.OnPickConfirm) },
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
                GroupEditorStep.ARRANGE -> ArrangeStep(state = state, listState = memberListState, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun PickGrid(wallpapers: List<WallpaperItem>, picked: Set<Int>, onClick: (Int) -> Unit) {
    LazyVerticalGrid(
        // At the end the last row stops above the check, not under it.
        contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = PickListEndPadding),
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
 * The members, and the panel over the end of their list. «Add wallpaper» keeps its place at the end of the
 * list; while that place is above the panel it is a pill there, and once scrolling takes the place down to the
 * panel it stays there as a round plus ([AddButtonPlacement]). Wherever the button comes near the panel, pill
 * or round, the panel cuts a cradle round it: deeper as it comes down, as wide as it is. Without members the
 * button sits under the empty state, in the middle above the panel.
 *
 * The list runs on behind the panel to a little below the deepest cradle: it shows through the cradle round
 * the button, never below the panel. A shade over the list's end, behind the panel and the button, sets them
 * off from the pictures. The overscroll stretch is drawn by a layer that holds the list, the panel and the
 * button, so that the button never comes apart from the list.
 */
@Composable
private fun ArrangeStep(state: GroupEditorState, listState: LazyListState, onEvent: (GroupEditorEvent) -> Unit) {
    val density = LocalDensity.current
    val overscrollEffect = rememberOverscrollEffect()
    var bodyHeight by remember { mutableIntStateOf(0) }
    var panelAreaHeight by remember { mutableIntStateOf(0) }
    var emptyContentHeight by remember { mutableIntStateOf(0) }
    val panelTop = bodyHeight - panelAreaHeight + with(density) { PanelMargin.toPx() }
    val isEmpty = state.isArrangeReady && state.memberItems.isEmpty()
    val addText = stringResource(R.string.add_wallpaper)
    val addPillWidth = rememberAddWallpaperPillWidth(addText)
    val addButton = if (state.isArrangeReady && bodyHeight > 0 && panelAreaHeight > 0) {
        rememberAddButtonPlacement(listState).apply {
            this.panelTop = panelTop
            // The empty state's content is centred above the panel and ends with the button's place.
            emptyTop = if (isEmpty && emptyContentHeight > 0) {
                (panelTop + emptyContentHeight) / 2 - with(density) { AddWallpaperButtonDefaults.Size.toPx() }
            } else {
                null
            }
        }
    } else {
        null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The button may reach over the panel, never into the app bar.
            .clipToBounds()
            .onSizeChanged { bodyHeight = it.height }
            .overscroll(overscrollEffect),
    ) {
        if (state.isArrangeReady) {
            MemberList(
                members = state.memberItems,
                showsHint = state.showsSwipeHint == true,
                listState = listState,
                overscrollEffect = overscrollEffect?.withoutVisualEffect(),
                onRemove = { onEvent(GroupEditorEvent.OnMemberRemove(it)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = (with(density) { panelAreaHeight.toDp() } - PanelMargin - ListReachIntoPanel).coerceAtLeast(0.dp),
                    ),
            )
        }
        AnimatedVisibility(
            visible = isEmpty,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { panelTop.coerceAtLeast(0f).toDp() }),
        ) {
            EmptyMembers(onContentSized = { emptyContentHeight = it })
        }
        if (panelAreaHeight > 0) {
            ListEndShade(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(x = 0, y = (panelTop - ShadeHeight.toPx()).roundToInt()) },
            )
        }
        ArrangePanel(
            slideDuration = state.slideDuration,
            memberCount = state.memberItems.size,
            canSave = state.canSave,
            cradle = {
                addButton?.let { button ->
                    with(density) {
                        val buttonWidth = AddWallpaperButtonDefaults.width(addPillWidth, button.morphProgress)
                        CradleGeometry(
                            radius = CradleRadius.toPx(),
                            halfStraight = ((buttonWidth - AddWallpaperButtonDefaults.Size) / 2).toPx(),
                            lift = panelTop - button.top() - (AddWallpaperButtonDefaults.Size / 2).toPx(),
                            fillet = CradleFilletRadius.toPx(),
                        )
                    }
                }
            },
            onDurationPick = { onEvent(GroupEditorEvent.OnDurationPick(it)) },
            onSave = { onEvent(GroupEditorEvent.OnSaveClick) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { panelAreaHeight = it.height }
                .padding(PanelMargin),
        )
        if (addButton != null) {
            AddWallpaperButton(
                text = addText,
                morphProgress = addButton.morphProgress,
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
    showsHint: Boolean,
    listState: LazyListState,
    overscrollEffect: OverscrollEffect?,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        overscrollEffect = overscrollEffect,
        // At the end the list stops above the panel, though it runs on behind it.
        contentPadding = PaddingValues(top = ListTopPadding, bottom = ListReachIntoPanel + PanelMargin),
        verticalArrangement = Arrangement.spacedBy(MemberSpacing),
        modifier = modifier,
    ) {
        if (showsHint && members.isNotEmpty()) {
            item(key = HINT_KEY) {
                Text(
                    text = stringResource(R.string.group_swipe_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .animateItem(),
                )
            }
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

/** No members: what the series is for, and below it the place of «Add wallpaper». */
@Composable
private fun EmptyMembers(onContentSized: (height: Int) -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .onSizeChanged { onContentSized(it.height) }
                .padding(horizontal = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.group_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.group_empty_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(AddButtonGap + AddWallpaperButtonDefaults.Size))
        }
    }
}

/**
 * Darkens the list's end towards the panel's top edge, and a little below it where the list shows through the
 * cradle. In the background's colour: over the background itself it shows nothing.
 */
@Composable
private fun ListEndShade(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.background
    val edge = ShadeHeight / (ShadeHeight + ListReachIntoPanel)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ShadeHeight + ListReachIntoPanel)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    edge / 2 to color.copy(alpha = SHADE_ALPHA / 2),
                    edge to color.copy(alpha = SHADE_ALPHA),
                    1f to color.copy(alpha = SHADE_ALPHA),
                ),
            ),
    )
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

/**
 * The time each member shows, then save; save waits for [GroupEditorState.MIN_MEMBERS]. Its top edge has
 * [cradle] cut into it, null for none. It is read while drawing, so the cradle follows the button through
 * every scroll without recomposing the panel.
 */
@Composable
private fun ArrangePanel(
    slideDuration: SlideDuration,
    memberCount: Int,
    canSave: Boolean,
    cradle: () -> CradleGeometry?,
    onDurationPick: (SlideDuration) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The clip also lets touches in the cradle through to the list behind it.
            .graphicsLayer {
                // A cradle that cuts nothing leaves the shape as it was, so the outline isn't made again.
                shape = CradleShape(PanelCornerRadius.toPx(), cradle()?.takeIf { it.cuts })
                clip = true
                shadowElevation = PanelElevation.toPx()
            },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(top = PanelContentTop, bottom = 16.dp)) {
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
 * reach its cradle on the panel; from there it stays in the cradle («pinned») as a round plus. Scrolling to
 * the end again, the list picks the button up and it turns back into the pill. Without members it sits at
 * [emptyTop].
 */
@Stable
private class AddButtonPlacement(
    private val listState: LazyListState,
    /** From the top of the button's place in the list to the button. */
    private val gap: Float,
    /** From the pinned button's top down to the panel's edge. */
    private val pinnedRise: Float,
    private val pinThreshold: Float,
) {
    /** The panel's top edge, from the top of the step. */
    var panelTop: Float by mutableFloatStateOf(0f)

    /** The button's top under the empty state while there are no members; null while there are. */
    var emptyTop: Float? by mutableStateOf(null)

    var morphProgress: Float by mutableFloatStateOf(0f)

    private val pinnedTop: Float get() = panelTop - pinnedRise

    /** The button's top at its place in the list, or null when that place is below what the list shows. */
    private fun listTopOrNull(): Float? {
        val layoutInfo = listState.layoutInfo
        // Item offsets start after the top content padding, which the viewport's start offset is minus.
        return layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == ADD_BUTTON_KEY }
            ?.let { item -> item.offset - layoutInfo.viewportStartOffset + gap }
    }

    val isPinned: Boolean by derivedStateOf {
        emptyTop == null && (listTopOrNull()?.let { top -> top > pinnedTop + pinThreshold } ?: true)
    }

    /** Where the button belongs now. */
    private fun targetTop(): Float = emptyTop ?: if (isPinned) pinnedTop else listTopOrNull() ?: pinnedTop

    private val movingTop = Animatable(0f)
    private var isFollowing by mutableStateOf(false)

    /**
     * Where the button is; read while placing, so that it follows every scroll without recomposing. While the
     * list scrolls it is exactly where it belongs and moves with the list; otherwise it springs there, as the
     * members do when one is taken out, or to the empty state when the last one is.
     */
    fun top(): Float = if (!isFollowing || listState.isScrollInProgress) targetTop() else movingTop.value

    /** Runs as long as the button shows. Where it first belongs it starts from, without moving. */
    suspend fun follow() {
        snapshotFlow { targetTop() to listState.isScrollInProgress }.collectLatest { (target, isScrolling) ->
            if (!isFollowing || isScrolling) {
                movingTop.snapTo(target)
                isFollowing = true
            } else {
                movingTop.animateTo(target, MoveSpec)
            }
        }
    }
}

@Composable
private fun rememberAddButtonPlacement(listState: LazyListState): AddButtonPlacement {
    val density = LocalDensity.current
    val placement = remember(listState, density) {
        with(density) {
            AddButtonPlacement(
                listState = listState,
                gap = AddButtonPlaceTop.toPx(),
                pinnedRise = (CradleLift + AddWallpaperButtonDefaults.Size / 2).toPx(),
                pinThreshold = 1.dp.toPx(),
            )
        }
    }
    LaunchedEffect(placement) { placement.follow() }
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
private const val HINT_KEY = "hint"

/** How far below its place the check FAB starts and ends, in its heights: off the screen. */
private const val FAB_TRAVEL = 3

/** The «+»-sized check (56 dp) and its 16 dp margin, and the grid's own 8 dp gap above it. */
private val PickListEndPadding = 56.dp + 16.dp + 8.dp

/** As the list moves its items: the button keeps up with the members around it. */
private val MoveSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)

private val ListTopPadding = 8.dp
private val MemberHeight = 120.dp
private val MemberSpacing = 8.dp

/** Between the last member and «Add wallpaper». */
private val AddButtonGap = 16.dp

/** From the top of the button's place in the list to the button: the list's spacing makes up the rest of the gap. */
private val AddButtonPlaceTop = AddButtonGap - MemberSpacing

/**
 * The cradle keeps [CradleMargin] round the button. The pinned button's centre is [CradleLift] above the panel,
 * the lowest it gets, so its cradle is the deepest.
 */
private val CradleMargin = 6.dp
private val CradleRadius = AddWallpaperButtonDefaults.Size / 2 + CradleMargin
private val CradleLift = 4.dp
private val CradleFilletRadius = 8.dp
private val CradleDepth = CradleRadius - CradleLift

/** Round the panel: it keeps this much from the screen's edges, and the list's end stops this far above it. */
private val PanelMargin = 16.dp
private val PanelCornerRadius = 28.dp
private val PanelElevation = 6.dp

/** The panel's content starts below the cradle. */
private val PanelContentTop = CradleDepth + 6.dp

/** How far the list runs on behind the panel: a little below the cradle, to show through it and nowhere else. */
private val ListReachIntoPanel = CradleDepth + 4.dp

/** The shade over the list's end starts this far above the panel, above the pinned button. */
private val ShadeHeight = 56.dp
private const val SHADE_ALPHA = 0.8f

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
            showsSwipeHint = true,
        ),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}
