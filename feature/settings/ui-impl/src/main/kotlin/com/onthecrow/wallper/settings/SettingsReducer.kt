package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class SettingsReducer @Inject constructor() : Reducer<SettingsState, SettingsEvent> {
    override suspend fun reduce(state: SettingsState, event: SettingsEvent): SettingsState =
        when (event) {
            is SettingsEvent.OnWallpaperChecked -> state.copy(isWallpaperSet = event.isSet)
            // A changed setting comes back from the store as the new settings.
            is SettingsEvent.OnSettingsChanged -> state.copy(settings = event.settings)
            is SettingsEvent.OnStorageCounted -> state.copy(storageBytes = event.bytes)
            SettingsEvent.OnPlaybackClick -> state.copy(dialog = SettingsDialog.PLAYBACK)
            SettingsEvent.OnThemeClick -> state.copy(dialog = SettingsDialog.THEME)
            is SettingsEvent.OnPlaybackSelect,
            is SettingsEvent.OnThemeSelect,
            SettingsEvent.OnDialogDismiss -> state.copy(dialog = null)
            SettingsEvent.OnSendReportClick -> state.copy(isReportInProgress = true)
            is SettingsEvent.ShareReport,
            SettingsEvent.OnReportFailed -> state.copy(isReportInProgress = false)
            else -> state
        }
}
