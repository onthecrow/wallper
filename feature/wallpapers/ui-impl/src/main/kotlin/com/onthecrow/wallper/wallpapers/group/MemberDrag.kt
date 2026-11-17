package com.onthecrow.wallper.wallpapers.group

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A member dragged up or down the list to change the order. A long press picks its row up ([start]); it follows
 * the finger ([offsetOf]), and the rows it passes the middle of make way for it ([order], which the list shows
 * meanwhile). Held near the top or the bottom of what the list shows, the list scrolls on by itself, faster the
 * nearer the edge. On the drop ([end]) the new order goes to the series; the list shows it until the series'
 * members are that order ([onMembersChanged]), so the rows don't jump back for a frame.
 *
 * Main thread only.
 */
@Stable
internal class MemberDrag(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val autoScrollZonePx: Float,
    private val maxAutoScrollPxPerSecond: Float,
    private val onSwap: () -> Unit,
) {
    /** The members' order from a drag until the series has it; null otherwise. */
    var order: List<Int>? by mutableStateOf(null)
        private set

    /** The member picked up; null between drags. */
    var draggedId: Int? by mutableStateOf(null)
        private set

    private var picked: List<Int> = emptyList()

    /** The dropped order went to the series: the next members it has end the drag's order. */
    private var committing = false

    /** Where the row was in the list when it was picked up, and how far the finger has gone since. */
    private var startOffset = 0
    private var fingerDelta by mutableFloatStateOf(0f)

    /** The index the list lays a member out at, in the order being dragged. */
    private var listIndexOf: (id: Int) -> Int = { -1 }
    private var autoScroll: Job? = null

    /** How far from where the list lays it out the row of [id] is drawn: under the finger while it is dragged. */
    fun offsetOf(id: Int): Float {
        if (id != draggedId) return 0f
        val item = itemOf(id) ?: return 0f
        return startOffset + fingerDelta - item.offset
    }

    /** Picks [id] up out of [members]; [listIndexOf] says where the list lays a member of the order out. */
    fun start(id: Int, members: List<Int>, listIndexOf: (orderIndex: Int) -> Int) {
        val item = itemOf(id) ?: return
        picked = members
        order = members
        draggedId = id
        committing = false
        startOffset = item.offset
        fingerDelta = 0f
        this.listIndexOf = { memberId -> order?.indexOf(memberId)?.takeIf { it >= 0 }?.let(listIndexOf) ?: -1 }
        autoScroll?.cancel()
        autoScroll = scope.launch { scrollWhileHeldAtAnEdge() }
    }

    fun drag(deltaY: Float) {
        if (draggedId == null) return
        fingerDelta += deltaY
        swapIfPassed()
    }

    /** Drops the row: the new order, or null if it is the one it had. */
    fun end(): List<Int>? {
        autoScroll?.cancel()
        autoScroll = null
        draggedId = null
        fingerDelta = 0f
        val dropped = order
        if (dropped == null || dropped == picked) {
            order = null
            return null
        }
        committing = true
        return dropped
    }

    /** The series' members changed: after a drop, they are the new order, and the list shows them again. */
    fun onMembersChanged() {
        if (!committing) return
        committing = false
        order = null
    }

    private fun itemOf(id: Int): LazyListItemInfo? = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    /** The row's middle has passed into another member's row: they change places. */
    private fun swapIfPassed() {
        val id = draggedId ?: return
        val current = order ?: return
        val item = itemOf(id) ?: return
        // The list hasn't laid the last change out yet: its rows are where they were.
        if (item.index != listIndexOf(id)) return
        val middle = startOffset + fingerDelta + item.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            val otherId = other.key as? Int
            otherId != null && otherId != id && otherId in current && middle >= other.offset && middle < other.offset + other.size
        } ?: return
        val first = listState.firstVisibleItemIndex
        // The list keeps its first row in place: a swap with it would scroll the list instead.
        if (item.index == first || target.index == first) {
            listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
        }
        order = MemberOrder.moved(current, id, to = target.key as Int)
        onSwap()
    }

    private suspend fun scrollWhileHeldAtAnEdge() {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nanos = withFrameNanos { it }
            val seconds = (nanos - lastNanos) / NANOS_PER_SECOND
            lastNanos = nanos
            val id = draggedId ?: return
            val item = itemOf(id) ?: continue
            val info = listState.layoutInfo
            val top = startOffset + fingerDelta
            val speed = MemberOrder.autoScrollSpeed(
                top = top,
                bottom = top + item.size,
                movedDown = fingerDelta > 0f,
                viewStart = info.viewportStartOffset.toFloat(),
                // The list runs on behind the panel; what shows of it ends above it.
                viewEnd = (info.viewportEndOffset - info.afterContentPadding).toFloat(),
                zone = autoScrollZonePx,
                maxPxPerSecond = maxAutoScrollPxPerSecond,
            )
            if (speed != 0f && listState.scrollBy(speed * seconds) != 0f) swapIfPassed()
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000f
    }
}

/** How a dragged member moves among the others. Free of Android types: tested on the JVM. */
internal object MemberOrder {
    /** [order] with [id] where [to] is, the members between moving one place towards where [id] was. */
    fun moved(order: List<Int>, id: Int, to: Int): List<Int> {
        val from = order.indexOf(id)
        val toIndex = order.indexOf(to)
        if (from < 0 || toIndex < 0 || from == toIndex) return order
        return order.toMutableList().apply { add(toIndex, removeAt(from)) }
    }

    /**
     * How fast to scroll, in px a second, for a row from [top] to [bottom] held where the list shows
     * [viewStart] to [viewEnd]: within [zone] of an edge it is moving towards ([movedDown] or up), faster the
     * further in, up to [maxPxPerSecond]; negative scrolls back up. Zero elsewhere.
     */
    fun autoScrollSpeed(
        top: Float,
        bottom: Float,
        movedDown: Boolean,
        viewStart: Float,
        viewEnd: Float,
        zone: Float,
        maxPxPerSecond: Float,
    ): Float = when {
        zone <= 0f -> 0f
        !movedDown && top < viewStart + zone -> -maxPxPerSecond * ((viewStart + zone - top) / zone).coerceIn(0f, 1f)
        movedDown && bottom > viewEnd - zone -> maxPxPerSecond * ((bottom - viewEnd + zone) / zone).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** The drag of [listState]'s members, with a tick at each change of places. */
@Composable
internal fun rememberMemberDrag(listState: LazyListState): MemberDrag {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    return remember(listState, scope, density) {
        with(density) {
            MemberDrag(
                listState = listState,
                scope = scope,
                autoScrollZonePx = AutoScrollZone.toPx(),
                maxAutoScrollPxPerSecond = AutoScrollMaxSpeed.toPx(),
                onSwap = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) },
            )
        }
    }
}

/** How near an edge of what the list shows a dragged row scrolls it. */
private val AutoScrollZone = 64.dp

/** Per second, a row deep into the edge: some six rows. */
private val AutoScrollMaxSpeed = 768.dp
