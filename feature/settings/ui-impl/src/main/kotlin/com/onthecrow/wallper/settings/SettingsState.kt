package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.mvi.State

internal data class SettingsState(
    /** Whether Wallper is the home screen's wallpaper; null until the first check. */
    val isWallpaperSet: Boolean? = null,
) : State
