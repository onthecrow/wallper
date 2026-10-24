package com.onthecrow.wallper.settings

import android.content.Context
import androidx.core.content.edit
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import javax.inject.Inject
import javax.inject.Singleton

/** [AppSettings] as one JSON string in device-protected SharedPreferences: the wallpaper reads them in direct boot. */
@Singleton
internal class AppSettingsStore @Inject constructor(@ApplicationContext context: Context) : AppSettingsSource {

    private val preferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        preferences.getString(KEY_SETTINGS, null)?.let(AppSettings::decode) ?: AppSettings()
    )
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = _settings.updateAndGet(transform)
        preferences.edit { putString(KEY_SETTINGS, next.encode()) }
    }

    private companion object {
        const val FILE = "app_settings"

        /** Bump the version (and read the older key once) if the JSON stops being compatible. */
        const val KEY_SETTINGS = "settings_v1"
    }
}
