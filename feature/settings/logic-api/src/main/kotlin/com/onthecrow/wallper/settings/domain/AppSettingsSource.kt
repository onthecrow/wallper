package com.onthecrow.wallper.settings.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * The app's settings, stored in device-protected storage: the wallpaper reads them in direct boot.
 * One instance per process, the only writer, so the screens and the wallpaper see the same flow.
 */
interface AppSettingsSource {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}
