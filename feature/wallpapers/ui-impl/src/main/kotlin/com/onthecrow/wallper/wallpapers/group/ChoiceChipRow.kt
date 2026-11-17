package com.onthecrow.wallper.wallpapers.group

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * [choices] as chips in a row that scrolls, [selected] ticked. Once [centresChoice] turns true, the row
 * scrolls the chosen one to its middle, as far as it goes: once, at the start, so that the user's own picks
 * don't move it.
 *
 * Each end fades into [edgeColor], the background's, where chips go on beyond it: the fade comes in over the
 * first [EdgeFadeWidth] of scrolling, so there is none at an end the row has reached.
 */
@Composable
internal fun <T> ChoiceChipRow(
    choices: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    centresChoice: Boolean,
    edgeColor: Color,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var viewportWidth by remember { mutableIntStateOf(0) }
    // The chosen chip and the middle of it in the row's content, once it is placed.
    var placedChoice by remember { mutableStateOf<Pair<T, Float>?>(null) }
    LaunchedEffect(centresChoice) {
        if (!centresChoice) return@LaunchedEffect
        val middle = snapshotFlow { placedChoice }.filterNotNull().first { (choice, _) -> choice == selected }.second
        val width = snapshotFlow { viewportWidth }.first { it > 0 }
        // Beyond the ends scrollTo stops at them.
        scroll.scrollTo((middle - width / 2f).roundToInt())
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .onSizeChanged { viewportWidth = it.width }
            // Drawn over the chips, outside the scroll: read while drawing, it follows the scroll without
            // recomposing.
            .drawWithContent {
                drawContent()
                val fade = EdgeFadeWidth.toPx()
                val start = (scroll.value / fade).coerceIn(0f, 1f)
                val end = ((scroll.maxValue - scroll.value) / fade).coerceIn(0f, 1f)
                if (start > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(edgeColor, Color.Transparent), endX = fade),
                        size = Size(fade, size.height),
                        alpha = start,
                    )
                }
                if (end > 0f) {
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, edgeColor), startX = size.width - fade, endX = size.width),
                        topLeft = Offset(size.width - fade, 0f),
                        size = Size(fade, size.height),
                        alpha = end,
                    )
                }
            }
            .horizontalScroll(scroll)
            .padding(horizontal = ChipsPadding),
    ) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onPick(choice) },
                label = { Text(text = label(choice)) },
                modifier = if (choice == selected) {
                    // Placed in the row inside its padding, which the scrolled content starts with.
                    Modifier.onPlaced { chip ->
                        val start = with(density) { ChipsPadding.toPx() } + chip.positionInParent().x
                        placedChoice = choice to start + chip.size.width / 2f
                    }
                } else {
                    Modifier
                },
            )
        }
    }
}

/** Before the first chip and after the last. */
private val ChipsPadding = 16.dp

/** How wide the row fades at an end with more beyond it. */
private val EdgeFadeWidth = 32.dp
