package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class SettingsReducer @Inject constructor() : Reducer<SettingsState, SettingsEvent> {
    override suspend fun reduce(state: SettingsState, event: SettingsEvent): SettingsState =
        when (event) {
            is SettingsEvent.OnWallpaperChecked -> state.copy(isWallpaperSet = event.isSet)
            else -> state
        }
}
