@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.settings.ui.R
import com.onthecrow.wallper.settings.view.SignatureFooter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    eventFlow: Flow<SettingsEvent>,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LaunchedEffect(eventFlow) {
        eventFlow.collect { event ->
            if (event is SettingsEvent.OpenLiveWallpaperChooser) context.startActivity(event.intent)
        }
    }
    // Back from the system wallpaper picker or from anywhere else the wallpaper could have changed.
    LifecycleResumeEffect(Unit) {
        onEvent(SettingsEvent.OnResume)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(SettingsEvent.OnBackClick) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // At least a screen tall, so the footer sits at the bottom; a longer list scrolls it along.
        BoxWithConstraints(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                WallpaperItem(
                    isSet = state.isWallpaperSet,
                    onSetClick = { onEvent(SettingsEvent.OnSetWallpaperClick) },
                )
                Spacer(modifier = Modifier.weight(1f))
                SignatureFooter(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WallpaperItem(isSet: Boolean?, onSetClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = stringResource(R.string.settings_wallpaper)) },
        trailingContent = { WallpaperStatus(isSet = isSet, onSetClick = onSetClick) },
    )
}

/**
 * A loader while the first check runs, then the Set button or «All set». Every state stays in the layout
 * and only fades, so the slot is as wide as the widest one and the text beside it never reflows.
 */
@Composable
private fun WallpaperStatus(isSet: Boolean?, onSetClick: () -> Unit) {
    // A check that answers at once shows no loader: it would only flash.
    var isLoaderDue by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADER_DELAY_MS)
        isLoaderDue = true
    }

    Box(contentAlignment = Alignment.Center) {
        FadeSlot(visible = isSet == false) {
            Button(
                // Hidden, it still takes touches: they do nothing.
                onClick = { if (isSet == false) onSetClick() },
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_power),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.settings_wallpaper_set_button))
            }
        }
        FadeSlot(visible = isSet == true) {
            AllSet()
        }
        FadeSlot(visible = isSet == null && isLoaderDue) {
            val description = stringResource(R.string.settings_wallpaper_checking)
            CircularProgressIndicator(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = description },
                strokeWidth = 3.dp,
            )
        }
    }
}

@Composable
private fun AllSet() {
    // Material palette greens, readable on the theme's surface.
    val color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Green300 else Green800
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            tint = color,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = stringResource(R.string.settings_wallpaper_all_set),
            color = color,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Fades and slightly scales [content] in and out; hidden, it keeps its place but leaves accessibility. */
@Composable
private fun FadeSlot(visible: Boolean, content: @Composable () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(FADE_MS),
        label = "status fade",
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = progress
                val scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * progress
                scaleX = scale
                scaleY = scale
            }
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val LOADER_DELAY_MS = 150L
private const val FADE_MS = 220
private const val HIDDEN_SCALE = 0.9f
private val Green300 = Color(0xFF81C784)
private val Green800 = Color(0xFF2E7D32)

@Preview
@Composable
private fun SettingsScreenCheckingPreview() {
    SettingsScreen(
        state = SettingsState(isWallpaperSet = null),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}

@Preview
@Composable
private fun SettingsScreenNotSetPreview() {
    SettingsScreen(
        state = SettingsState(isWallpaperSet = false),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}

@Preview
@Composable
private fun SettingsScreenSetPreview() {
    SettingsScreen(
        state = SettingsState(isWallpaperSet = true),
        eventFlow = emptyFlow(),
        onEvent = {},
    )
}
