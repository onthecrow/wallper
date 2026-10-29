package com.onthecrow.wallper.settings

import android.content.Intent
import com.onthecrow.wallper.core.model.VideoPlayback
import com.onthecrow.wallper.core.mvi.Event
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.ThemeMode

internal sealed interface SettingsEvent : Event {
    // User actions
    data object OnSetWallpaperClick : SettingsEvent
    data object OnBackClick : SettingsEvent
    data object OnPlaybackClick : SettingsEvent
    data class OnPlaybackSelect(val playback: VideoPlayback) : SettingsEvent
    data class OnSaveBatteryChange(val enabled: Boolean) : SettingsEvent
    data class OnScrollWithPagesChange(val enabled: Boolean) : SettingsEvent
    data class OnActivateNewWallpapersChange(val enabled: Boolean) : SettingsEvent
    data class OnShowBuiltInWallpapersChange(val enabled: Boolean) : SettingsEvent
    data object OnStorageClick : SettingsEvent
    data object OnThemeClick : SettingsEvent
    data class OnThemeSelect(val theme: ThemeMode) : SettingsEvent
    data class OnDynamicColorChange(val enabled: Boolean) : SettingsEvent
    data class OnShareUsageAndCrashDataChange(val enabled: Boolean) : SettingsEvent
    data object OnDialogDismiss : SettingsEvent
    data object OnSendReportClick : SettingsEvent

    // The screen is shown again, e.g. back from the system wallpaper picker
    data object OnResume : SettingsEvent

    // Internal events
    data class OnWallpaperChecked(val isSet: Boolean) : SettingsEvent
    data class OnSettingsChanged(val settings: AppSettings) : SettingsEvent
    data class OnStorageCounted(val bytes: Long) : SettingsEvent

    // One-off effects, handled by the screen
    data class OpenLiveWallpaperChooser(val intent: Intent) : SettingsEvent
    /** The report at [path] is ready to share. */
    data class ShareReport(val path: String) : SettingsEvent
    data object OnReportFailed : SettingsEvent
}
