package com.onthecrow.wallper.editor.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.onthecrow.wallper.editor.domain.model.ExportOutput
import com.onthecrow.wallper.editor.domain.model.ExportStatus
import com.onthecrow.wallper.editor.ui.R
import kotlin.math.roundToLong

/**
 * An optimised video being made: what it will be ([ExportOutput]: size, length, codec), how far it is and about
 * how long it still takes. Only Cancel closes it: a stray back swipe or tap beside it would throw a long export
 * away.
 */
@Composable
internal fun ExportProgressDialog(status: ExportStatus.InProgress, onCancel: () -> Unit) {
    // Transformer's percent comes twice a second: the bar fills in between.
    val progress by animateFloatAsState(
        targetValue = status.progress / 100f,
        animationSpec = tween(durationMillis = PROGRESS_STEP_MS, easing = LinearEasing),
        label = "export progress",
    )
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = { Text(text = stringResource(R.string.editor_export_title)) },
        text = {
            Column {
                status.output?.let { output ->
                    Text(text = output.summary(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.editor_export_percent, status.progress),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        // Wide enough for 100 %: the bar doesn't move as the digits change.
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .widthIn(min = 40.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = timeLeft(ExportTimeLeft.of(status.remainingMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

/** «1080 × 2400 · 0:12 · H.264»; the length only where it is known. */
private fun ExportOutput.summary(): String = listOfNotNull(
    "$width × $height",
    durationMs.takeIf { it > 0 }?.let(::clipLength),
    codec,
).joinToString(" · ")

/** m:ss, at least a second. */
private fun clipLength(durationMs: Long): String {
    val seconds = (durationMs / 1000.0).roundToLong().coerceAtLeast(1)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun timeLeft(estimate: ExportTimeLeft.Estimate): String = when (estimate) {
    ExportTimeLeft.Estimate.Unknown -> stringResource(R.string.editor_export_estimating)
    ExportTimeLeft.Estimate.AlmostDone -> stringResource(R.string.editor_export_almost_done)
    is ExportTimeLeft.Estimate.Seconds -> stringResource(R.string.editor_export_seconds_left, estimate.seconds)
    is ExportTimeLeft.Estimate.Minutes -> stringResource(R.string.editor_export_minutes_left, estimate.minutes)
}

/** How the dialog says the time an export still takes. Free of Android types: tested on the JVM. */
internal object ExportTimeLeft {
    sealed interface Estimate {
        /** Too early to tell. */
        data object Unknown : Estimate
        data object AlmostDone : Estimate

        /** Under a minute, rounded up to [SECONDS_STEP]. */
        data class Seconds(val seconds: Int) : Estimate

        /** A minute or more, rounded. */
        data class Minutes(val minutes: Int) : Estimate
    }

    const val ALMOST_DONE_MS = 5_000L
    const val SECONDS_STEP = 5

    /** [remainingMs] as the dialog says it; null is too early to tell. */
    fun of(remainingMs: Long?): Estimate {
        if (remainingMs == null) return Estimate.Unknown
        if (remainingMs < ALMOST_DONE_MS) return Estimate.AlmostDone
        val stepMs = SECONDS_STEP * 1_000L
        val seconds = ((remainingMs + stepMs - 1) / stepMs * SECONDS_STEP).toInt()
        if (seconds < 60) return Estimate.Seconds(seconds)
        return Estimate.Minutes((remainingMs / 60_000.0).roundToLong().toInt().coerceAtLeast(1))
    }
}

private const val PROGRESS_STEP_MS = 500

@Preview
@Composable
private fun ExportProgressDialogPreview() {
    ExportProgressDialog(
        status = ExportStatus.InProgress(48, 40_000, ExportOutput(1080, 2400, 12_000, "H.264")),
        onCancel = {},
    )
}
