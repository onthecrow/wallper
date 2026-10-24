package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.mvi.State
import com.onthecrow.wallper.settings.domain.AppSettings

internal data class SettingsState(
    /** Whether Wallper is the home screen's wallpaper; null until the first check. */
    val isWallpaperSet: Boolean? = null,
    val settings: AppSettings = AppSettings(),
    /** What the user's wallpapers take, null until counted. */
    val storageBytes: Long? = null,
    /** The choice open in a dialog, null when none is. */
    val dialog: SettingsDialog? = null,
    /** A report is being written: its row waits for it. */
    val isReportInProgress: Boolean = false,
) : State

internal enum class SettingsDialog { PLAYBACK, THEME }
