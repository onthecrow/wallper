package com.onthecrow.wallper.diagnostics

import android.app.Application
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

/**
 * Debug builds log to logcat and a file and report nothing: Firebase isn't in them, so «Share usage and crash data»
 * has nothing to turn off.
 */
internal fun Application.installDiagnostics(appSettings: AppSettingsSource, scope: CoroutineScope) {
    Timber.plant(Timber.DebugTree())
    Timber.plant(DebugFileTree(this))
}
