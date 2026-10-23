package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.onthecrow.wallper.core.blackhole.physics.QualityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where Auto quality settled on this device, so that a new engine starts there instead of on High and
 * stepping down again. Forgotten after an update of the app or of the system (new drivers), and when the
 * user picks Auto again. Device-protected: the wallpaper reads it in direct boot.
 */
@Singleton
internal class AutoQualityMemory @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val prefs by lazy {
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private val build by lazy {
        "${Build.FINGERPRINT}|${context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime}"
    }

    fun load(): QualityLevel? {
        if (prefs.getString(KEY_BUILD, null) != build) return null
        val name = prefs.getString(KEY_LEVEL, null) ?: return null
        return QualityLevel.entries.find { it.name == name }
    }

    fun save(level: QualityLevel) = prefs.edit {
        putString(KEY_BUILD, build)
        putString(KEY_LEVEL, level.name)
    }

    fun clear() = prefs.edit { clear() }

    private companion object {
        const val PREFS = "auto_quality"
        const val KEY_BUILD = "build"
        const val KEY_LEVEL = "level"
    }
}
