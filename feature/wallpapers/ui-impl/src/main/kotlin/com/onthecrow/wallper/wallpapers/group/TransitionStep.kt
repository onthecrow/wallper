package com.onthecrow.wallper.wallpapers.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.core.designsystem.component.phoneframe.PhoneFramedScreen
import com.onthecrow.wallper.core.designsystem.component.phoneframe.rememberScreenAspectRatio
import com.onthecrow.wallper.core.model.SlideTransition
import com.onthecrow.wallper.core.model.TransitionEffect
import com.onthecrow.wallper.wallpapers.preview.SeriesPreviewSpec
import com.onthecrow.wallper.wallpapers.preview.SeriesPreviewSurface
import com.onthecrow.wallper.wallpapers.preview.rememberSeriesPreviewState
import com.onthecrow.wallper.wallpapers.ui.R
import kotlin.math.roundToLong

/**
 * The series' transition: its first two members going one into the other and back through it, round and round,
 * in a phone with the screen's proportions, each alone for a couple of seconds between: what a transition looks
 * like has nothing to do with how long a member stays on the wallpaper, an hour maybe. Below, on a panel, the
 * transitions to pick from and how long one takes. A pick shows at once: the loop starts again with it.
 */
@Composable
internal fun TransitionStep(state: GroupEditorState, onEvent: (GroupEditorEvent) -> Unit) {
    val transition = state.transition
    val preview = rememberSeriesPreviewState(
        spec = state.previewSlides?.let { slides -> SeriesPreviewSpec(slides, transition, PREVIEW_HOLD_MS) },
    )
    Column(modifier = Modifier.fillMaxSize()) {
        PhoneFramedScreen(
            aspectRatio = rememberScreenAspectRatio(),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            SeriesPreviewSurface(preview, Modifier.fillMaxSize())
        }
        TransitionPanel(
            transition = transition,
            onEffectPick = { onEvent(GroupEditorEvent.OnTransitionEffectPick(it)) },
            onDurationPick = { onEvent(GroupEditorEvent.OnTransitionDurationPick(it)) },
            modifier = Modifier.padding(start = PanelMargin, end = PanelMargin, bottom = PanelMargin),
        )
    }
}

/** The transitions ([ChoiceChipRow], the chosen one in the middle) and, unless there is none, how long it takes. */
@Composable
private fun TransitionPanel(
    transition: SlideTransition,
    onEffectPick: (TransitionEffect) -> Unit,
    onDurationPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PanelCornerRadius),
        color = panelColor,
        shadowElevation = PanelElevation,
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            ChoiceChipRow(
                choices = TransitionEffect.entries,
                selected = transition.effect,
                label = { it.label() },
                centresChoice = true,
                edgeColor = panelColor,
                onPick = onEffectPick,
            )
            DurationSlider(
                durationMs = transition.durationMs,
                enabled = !transition.isNone,
                onPick = onDurationPick,
            )
        }
    }
}

/**
 * How long a transition takes, in steps of [SlideTransition.DURATION_STEP_MS]: the thumb snaps to them without
 * a mark for each, which would be twenty dots. The number follows the thumb; the pick is told once it is let
 * go of, as each one starts the preview again.
 */
@Composable
private fun DurationSlider(durationMs: Long, enabled: Boolean, onPick: (Long) -> Unit) {
    var dragged by remember(durationMs) { mutableFloatStateOf(durationMs.toFloat()) }
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.group_transition_duration),
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
        )
        Slider(
            value = dragged,
            onValueChange = { dragged = SlideTransition.duration(it.roundToLong()).toFloat() },
            onValueChangeFinished = { onPick(dragged.roundToLong()) },
            enabled = enabled,
            valueRange = SlideTransition.MIN_DURATION_MS.toFloat()..SlideTransition.MAX_DURATION_MS.toFloat(),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = stringResource(R.string.group_transition_seconds, dragged / 1_000f),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}

@Composable
internal fun TransitionEffect.label(): String = stringResource(
    when (this) {
        TransitionEffect.NONE -> R.string.transition_none
        TransitionEffect.CROSSFADE -> R.string.transition_crossfade
        TransitionEffect.DIP_TO_BLACK -> R.string.transition_dip_to_black
        TransitionEffect.BLUR -> R.string.transition_blur
        TransitionEffect.ZOOM -> R.string.transition_zoom
        TransitionEffect.SLIDE_UP -> R.string.transition_slide_up
        TransitionEffect.LUMA -> R.string.transition_luma
        TransitionEffect.WIPE -> R.string.transition_wipe
        TransitionEffect.IRIS -> R.string.transition_iris
        TransitionEffect.RIPPLE -> R.string.transition_ripple
        TransitionEffect.PIXELATE -> R.string.transition_pixelate
    }
)

/** Each member alone between two transitions: a couple of seconds to see it, not long to wait for the next. */
private const val PREVIEW_HOLD_MS = 2_000L

private const val DISABLED_ALPHA = 0.38f

@Preview
@Composable
private fun TransitionStepPreview() {
    TransitionStep(
        state = GroupEditorState(
            step = GroupEditorStep.TRANSITION,
            transition = SlideTransition(TransitionEffect.CROSSFADE),
            offersTransitions = true,
        ),
        onEvent = {},
    )
}
