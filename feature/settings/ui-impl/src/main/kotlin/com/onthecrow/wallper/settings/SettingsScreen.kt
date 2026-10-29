@file:OptIn(ExperimentalMaterial3Api::class)

package com.onthecrow.wallper.settings

import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.ThemeMode
import com.onthecrow.wallper.settings.ui.R
import com.onthecrow.wallper.settings.view.AppVersion
import com.onthecrow.wallper.settings.view.ChoiceDialog
import com.onthecrow.wallper.settings.view.ClickableItem
import com.onthecrow.wallper.settings.view.SectionHeader
import com.onthecrow.wallper.settings.view.SignatureFooter
import com.onthecrow.wallper.settings.view.SwitchItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import timber.log.Timber
import java.io.File

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    eventFlow: Flow<SettingsEvent>,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reportSubject = stringResource(R.string.settings_report_subject)
    val reportChooserTitle = stringResource(R.string.settings_report_chooser)
    val reportFailed = stringResource(R.string.settings_report_failed)

    LaunchedEffect(eventFlow) {
        eventFlow.collect { event ->
            when (event) {
                is SettingsEvent.OpenLiveWallpaperChooser -> context.startActivity(event.intent)
                is SettingsEvent.ShareReport -> ShareCompat.IntentBuilder(context)
                    .setType("text/plain")
                    .setStream(FileProvider.getUriForFile(context, "${context.packageName}$REPORTS_AUTHORITY", File(event.path)))
                    .setSubject(reportSubject)
                    .setChooserTitle(reportChooserTitle)
                    .startChooser()
                SettingsEvent.OnReportFailed -> Toast.makeText(context, reportFailed, Toast.LENGTH_LONG).show()
                else -> Unit
            }
        }
    }
    // Back from the system wallpaper picker or from anywhere else the wallpaper could have changed.
    LifecycleResumeEffect(Unit) {
        onEvent(SettingsEvent.OnResume)
        onPauseOrDispose { }
    }

    // A plain bar: a large collapsing title only takes the room of the settings.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                scrollBehavior = scrollBehavior,
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
                // Every wallpaper follows it, not only videos: it sits with the wallpaper, above the sections.
                SwitchItem(
                    title = stringResource(R.string.settings_scroll_with_pages),
                    summary = stringResource(R.string.settings_scroll_with_pages_summary),
                    checked = state.settings.scrollVideoWithPages,
                    onCheckedChange = { onEvent(SettingsEvent.OnScrollWithPagesChange(it)) },
                )
                VideoSection(state.settings, onEvent)
                ListSection(state.settings, state.storageBytes, onEvent)
                AppearanceSection(state.settings, onEvent)
                PrivacySection(state.settings, onEvent)
                HelpSection(state.isReportInProgress, onEvent)
                Spacer(modifier = Modifier.weight(1f))
                SignatureFooter(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                )
                AppVersion(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                )
            }
        }
    }

    when (state.dialog) {
        SettingsDialog.PLAYBACK -> ChoiceDialog(
            title = stringResource(R.string.settings_playback),
            options = VideoPlayback.entries,
            selected = state.settings.videoPlayback,
            label = { stringResource(it.labelRes) },
            description = { stringResource(it.descriptionRes) },
            onSelect = { onEvent(SettingsEvent.OnPlaybackSelect(it)) },
            onDismiss = { onEvent(SettingsEvent.OnDialogDismiss) },
        )
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = state.settings.theme,
            label = { stringResource(it.labelRes) },
            description = { null },
            onSelect = { onEvent(SettingsEvent.OnThemeSelect(it)) },
            onDismiss = { onEvent(SettingsEvent.OnDialogDismiss) },
        )
        null -> Unit
    }
}

@Composable
private fun VideoSection(settings: AppSettings, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(text = stringResource(R.string.settings_section_video))
    ClickableItem(
        title = stringResource(R.string.settings_playback),
        summary = stringResource(settings.videoPlayback.labelRes),
        onClick = { onEvent(SettingsEvent.OnPlaybackClick) },
    )
    SwitchItem(
        title = stringResource(R.string.settings_save_battery),
        summary = stringResource(R.string.settings_save_battery_summary),
        checked = settings.pauseVideoToSaveBattery,
        onCheckedChange = { onEvent(SettingsEvent.OnSaveBatteryChange(it)) },
    )
}

@Composable
private fun ListSection(settings: AppSettings, storageBytes: Long?, onEvent: (SettingsEvent) -> Unit) {
    val context = LocalContext.current
    SectionHeader(text = stringResource(R.string.settings_section_list))
    SwitchItem(
        title = stringResource(R.string.settings_activate_new),
        summary = stringResource(R.string.settings_activate_new_summary),
        checked = settings.activateNewWallpapers,
        onCheckedChange = { onEvent(SettingsEvent.OnActivateNewWallpapersChange(it)) },
    )
    SwitchItem(
        title = stringResource(R.string.settings_show_built_ins),
        summary = stringResource(R.string.settings_show_built_ins_summary),
        checked = settings.showBuiltInWallpapers,
        onCheckedChange = { onEvent(SettingsEvent.OnShowBuiltInWallpapersChange(it)) },
    )
    ClickableItem(
        title = stringResource(R.string.settings_storage),
        summary = storageBytes?.let { stringResource(R.string.settings_storage_summary, Formatter.formatShortFileSize(context, it)) }
            ?: stringResource(R.string.settings_storage_counting),
        onClick = { onEvent(SettingsEvent.OnStorageClick) },
    )
}

@Composable
private fun AppearanceSection(settings: AppSettings, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(text = stringResource(R.string.settings_section_appearance))
    ClickableItem(
        title = stringResource(R.string.settings_theme),
        summary = stringResource(settings.theme.labelRes),
        onClick = { onEvent(SettingsEvent.OnThemeClick) },
    )
    // The system's colours (Material You) exist from Android 12: from the wallpaper or picked by the user.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchItem(
            title = stringResource(R.string.settings_system_colors),
            summary = stringResource(R.string.settings_system_colors_summary),
            checked = settings.dynamicColor,
            onCheckedChange = { onEvent(SettingsEvent.OnDynamicColorChange(it)) },
        )
    }
}

@Composable
private fun HelpSection(isReportInProgress: Boolean, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(text = stringResource(R.string.settings_section_help))
    ClickableItem(
        title = stringResource(R.string.settings_send_report),
        summary = stringResource(R.string.settings_send_report_summary),
        onClick = { onEvent(SettingsEvent.OnSendReportClick) },
        enabled = !isReportInProgress,
        trailingContent = if (isReportInProgress) {
            { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp) }
        } else {
            null
        },
    )
}

@Composable
private fun PrivacySection(settings: AppSettings, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(text = stringResource(R.string.settings_section_privacy))
    SwitchItem(
        title = stringResource(R.string.settings_share_data),
        summary = stringResource(R.string.settings_share_data_summary),
        checked = settings.shareUsageAndCrashData,
        onCheckedChange = { onEvent(SettingsEvent.OnShareUsageAndCrashDataChange(it)) },
    )
    val uriHandler = LocalUriHandler.current
    ClickableItem(
        title = stringResource(R.string.settings_privacy_policy),
        summary = stringResource(R.string.settings_privacy_policy_summary),
        onClick = {
            runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) }
                .onFailure { Timber.w(it, "Can't open %s", PRIVACY_POLICY_URL) }
        },
    )
}

/** docs/privacy-policy.html, served by GitHub Pages from main. */
private const val PRIVACY_POLICY_URL = "https://onthecrow.github.io/wallper/privacy-policy.html"

private val VideoPlayback.labelRes: Int
    get() = when (this) {
        VideoPlayback.LOOP -> R.string.settings_playback_loop
        VideoPlayback.RESTART -> R.string.settings_playback_restart
        VideoPlayback.ONCE -> R.string.settings_playback_once
    }

private val VideoPlayback.descriptionRes: Int
    get() = when (this) {
        VideoPlayback.LOOP -> R.string.settings_playback_loop_description
        VideoPlayback.RESTART -> R.string.settings_playback_restart_description
        VideoPlayback.ONCE -> R.string.settings_playback_once_description
    }

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
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

/** Appended to the package name; the FileProvider in this module's manifest has the same authority. */
private const val REPORTS_AUTHORITY = ".reports"
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
