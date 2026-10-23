package com.onthecrow.wallper.blackhole.data

import android.content.Context
import androidx.core.content.edit
import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BlackHoleSettings] as one JSON string in device-protected SharedPreferences: the wallpaper reads them
 * in direct boot. One instance per process and the only writer, so the settings screen and the wallpaper
 * see the same flow.
 */
@Singleton
internal class BlackHoleSettingsStore @Inject constructor(@ApplicationContext context: Context) : BlackHoleSettingsSource {

    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<BlackHoleSettings> = _settings.asStateFlow()

    override fun update(transform: (BlackHoleSettings) -> BlackHoleSettings) {
        val next = _settings.updateAndGet(transform)
        preferences.edit { putString(KEY_SETTINGS, next.encode()) }
    }

    private fun read(): BlackHoleSettings {
        preferences.getString(KEY_SETTINGS, null)?.let { return BlackHoleSettings.decode(it) }
        val legacy = preferences.all
        if (legacy.isEmpty()) return BlackHoleSettings()
        // The first versions stored one key per setting: move them to JSON once.
        val settings = BlackHoleSettings.fromLegacyPreferences(legacy)
        preferences.edit {
            clear()
            putString(KEY_SETTINGS, settings.encode())
        }
        return settings
    }

    private companion object {
        const val FILE = "black_hole_settings"

        /** Bump the version (and read the older key once) if the JSON stops being compatible. */
        const val KEY_SETTINGS = "settings_v1"
    }
}
