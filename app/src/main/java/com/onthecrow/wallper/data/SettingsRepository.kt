package com.onthecrow.wallper.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.onthecrow.wallper.data.model.ScreenResolution
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_STORE_NAME)

    suspend fun getScreenResolution(): ScreenResolution {
        return context.dataStore.data.map { preferences ->
            ScreenResolution(
                width = preferences[KEY_SCREEN_WIDTH] ?: return@map null,
                height = preferences[KEY_SCREEN_HEIGHT] ?: return@map null,
            )
        }.filterNotNull()
            .first()
    }

    suspend fun putScreenResolution(screenResolution: ScreenResolution) {
        context.dataStore.edit { settings ->
            settings[KEY_SCREEN_WIDTH] = screenResolution.width
            settings[KEY_SCREEN_HEIGHT] = screenResolution.height
        }
    }

    companion object {
        private const val SETTINGS_STORE_NAME = "settings"
        private val KEY_SCREEN_WIDTH = floatPreferencesKey("screen-width")
        private val KEY_SCREEN_HEIGHT = floatPreferencesKey("screen-height")
    }
}