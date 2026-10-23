package com.onthecrow.wallper.settings

import android.content.Intent
import com.onthecrow.wallper.core.mvi.Event

internal sealed interface SettingsEvent : Event {
    // User actions
    data object OnSetWallpaperClick : SettingsEvent
    data object OnBackClick : SettingsEvent

    // The screen is shown again, e.g. back from the system wallpaper picker
    data object OnResume : SettingsEvent

    // Internal events
    data class OnWallpaperChecked(val isSet: Boolean) : SettingsEvent

    // One-off effects, handled by the screen
    data class OpenLiveWallpaperChooser(val intent: Intent) : SettingsEvent
}
