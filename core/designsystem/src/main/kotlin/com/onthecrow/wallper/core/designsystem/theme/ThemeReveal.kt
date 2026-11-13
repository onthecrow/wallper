package com.onthecrow.wallper.core.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val REVEAL_DURATION_MS = 500
private const val THEME_SWITCH_TIMEOUT_MS = 1_000L

/**
 * The circle reveal of a theme change, as in NomadRates: the old theme is frozen into a snapshot that covers
 * the screen, and a growing hole in it uncovers the new theme.
 *
 * Read it from [LocalThemeRevealState]. Outside a [ThemeRevealHost] it applies themes without animating.
 */
@Stable
class ThemeRevealState {

    /** Whether the old theme was dark while its snapshot still covers part of the screen, `null` otherwise. */
    var outgoingDarkTheme: Boolean? by mutableStateOf(null)
        private set

    internal var snapshot: ImageBitmap? by mutableStateOf(null)
        private set
    internal var center: Offset by mutableStateOf(Offset.Zero)
        private set
    internal val radius = Animatable(0f)

    internal var darkTheme: Boolean by mutableStateOf(false)
    internal var scope: CoroutineScope? = null
    internal var graphicsLayer: GraphicsLayer? = null
    internal var coordinates: LayoutCoordinates? = null
    private var job: Job? = null

    /**
     * Switches the theme behind a circle reveal.
     *
     * @param originOnScreen where the circle starts, in screen coordinates ([LayoutCoordinates.localToScreen]):
     * a dialog is a window of its own. The middle of the host when unspecified.
     * @param toDarkTheme whether the theme being applied is dark. Nothing is animated when it already matches
     * the current one.
     * @param applyTheme switches the theme; always called exactly once.
     */
    fun reveal(
        originOnScreen: Offset,
        toDarkTheme: Boolean,
        applyTheme: () -> Unit,
    ) {
        val scope = scope
        val layer = graphicsLayer
        val host = coordinates
        if (
            scope == null || layer == null || host == null || !host.isAttached ||
            toDarkTheme == darkTheme || job?.isActive == true
        ) {
            applyTheme()
            return
        }

        job = scope.launch {
            var isApplied = false
            try {
                val bitmap = layer.captureOrNull()
                if (bitmap != null && host.isAttached) {
                    center = host.localOrigin(originOnScreen)
                    radius.snapTo(0f)
                    snapshot = bitmap
                    outgoingDarkTheme = darkTheme
                }
                isApplied = true
                applyTheme()
                if (snapshot == null) return@launch

                // The new theme reaches the host at its next composition: the hole must not open before it does.
                val isSwitched = withTimeoutOrNull(THEME_SWITCH_TIMEOUT_MS) {
                    snapshotFlow { darkTheme }.first { it == toDarkTheme }
                } != null
                if (isSwitched) {
                    val size = host.size
                    radius.animateTo(
                        targetValue = farthestCornerDistance(center.x, center.y, size.width.toFloat(), size.height.toFloat()),
                        animationSpec = tween(REVEAL_DURATION_MS, easing = FastOutSlowInEasing),
                    )
                }
            } finally {
                if (!isApplied) applyTheme()
                snapshot = null
                outgoingDarkTheme = null
            }
        }
    }

    private fun LayoutCoordinates.localOrigin(originOnScreen: Offset): Offset {
        val local = if (originOnScreen.isSpecified) screenToLocal(originOnScreen) else Offset.Unspecified
        return if (local.isSpecified) local else size.center.toOffset()
    }
}

val LocalThemeRevealState = staticCompositionLocalOf { ThemeRevealState() }

@Composable
fun rememberThemeRevealState(): ThemeRevealState = remember { ThemeRevealState() }

/**
 * Hosts [content] so that [ThemeRevealState.reveal] can animate theme changes over it, and provides [state]
 * through [LocalThemeRevealState].
 *
 * @param darkTheme the theme [content] is drawn with now.
 */
@Composable
fun ThemeRevealHost(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    state: ThemeRevealState = rememberThemeRevealState(),
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    DisposableEffect(state, scope, graphicsLayer) {
        state.scope = scope
        state.graphicsLayer = graphicsLayer
        onDispose {
            state.scope = null
            state.graphicsLayer = null
            state.coordinates = null
        }
    }
    SideEffect { state.darkTheme = darkTheme }

    CompositionLocalProvider(LocalThemeRevealState provides state) {
        Box(
            modifier = modifier.onPlaced { state.coordinates = it },
            propagateMinConstraints = true,
        ) {
            Box(
                modifier = Modifier.drawWithContent {
                    // Every frame is kept in the layer, so the current look can be captured on demand.
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                },
                propagateMinConstraints = true,
            ) {
                content()
            }
            ThemeRevealOverlay(state)
        }
    }
}

@Composable
private fun BoxScope.ThemeRevealOverlay(state: ThemeRevealState) {
    val snapshot = state.snapshot ?: return
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) {
                // Nothing underneath takes touches while the old theme still covers it.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            // Offscreen, so the punched hole clears only the snapshot and keeps an anti-aliased edge.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                drawImage(snapshot)
                drawCircle(
                    color = Color.Black,
                    radius = state.radius.value,
                    center = state.center,
                    blendMode = BlendMode.DstOut,
                )
            },
    )
}

private suspend fun GraphicsLayer.captureOrNull(): ImageBitmap? = try {
    toImageBitmap()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // E.g. a software capture of hardware bitmaps before Android 9: the theme switches without the reveal.
    null
}
