package com.onthecrow.wallper.settings.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toOffset

/** A section's title, as in Pixel's settings: small, in the accent colour, a heading for screen readers. */
@Composable
internal fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

/** A setting turned on and off. The whole row toggles it, and screen readers read it as one switch. */
@Composable
internal fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

/** A setting that opens a choice, another screen or an action; [summary] is its current value. */
@Composable
internal fun ClickableItem(
    title: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = summary) },
        trailingContent = trailingContent,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

/**
 * One of [options], as Pixel's settings ask it: a tap picks it and closes the dialog, Cancel or back
 * keeps the current one.
 *
 * @param onSelect gets the option and where it was pressed, in screen coordinates ([Offset.Unspecified] if
 * unknown): the theme's reveal starts there.
 */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String?,
    onSelect: (option: T, pressOnScreen: Offset) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    ChoiceRow(
                        label = label(option),
                        description = description(option),
                        selected = option == selected,
                        onClick = { pressOnScreen -> onSelect(option, pressOnScreen) },
                    )
                }
            }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, description: String?, selected: Boolean, onClick: (pressOnScreen: Offset) -> Unit) {
    val press = remember { PressTracker() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .onPlaced { press.coordinates = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    press.position = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
                }
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onClick(press.positionOnScreen()) })
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The last press on a row, so that its click can tell where it happened on the screen. */
private class PressTracker {
    var coordinates: LayoutCoordinates? = null
    var position: Offset = Offset.Unspecified

    fun positionOnScreen(): Offset {
        val coordinates = coordinates?.takeIf { it.isAttached } ?: return Offset.Unspecified
        // A click without a touch (keyboard, accessibility) starts from the row's centre.
        val local = if (position.isSpecified) position else coordinates.size.center.toOffset()
        return coordinates.localToScreen(local)
    }
}
