package com.onthecrow.wallper.diagnostics

import android.app.Application
import android.content.Context
import android.os.UserManager
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.onthecrow.wallper.core.diagnostics.Diagnostics
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Release builds report to Crashlytics and logcat. Firebase starts itself (`FirebaseInitProvider`); Analytics and
 * Crashlytics collect while the user lets them («Share usage and crash data»).
 */
internal fun Application.installDiagnostics(appSettings: AppSettingsSource, scope: CoroutineScope) {
    Diagnostics.reporter = FirebaseCrashReporter(this)
    appSettings.settings
        .map { it.shareUsageAndCrashData }
        .distinctUntilChanged()
        .onEach { enabled -> applyDataCollection(enabled) }
        .launchIn(scope)
}

/**
 * Firebase keeps the choice across runs. It can't be touched before the first unlock, but then it still has the last
 * choice: the switch can't change before the unlock either.
 */
private fun Context.applyDataCollection(enabled: Boolean) {
    if (!getSystemService(UserManager::class.java).isUserUnlocked || FirebaseApp.getApps(this).isEmpty()) return
    val crashlytics = FirebaseCrashlytics.getInstance()
    // Crashes from while it was off wait on the phone and would be sent once it's on: delete them first. The deletion
    // settles the waiting reports at once, the switch only through the main thread, so the deletion wins.
    if (enabled && !crashlytics.isCrashlyticsCollectionEnabled) crashlytics.deleteUnsentReports()
    crashlytics.setCrashlyticsCollectionEnabled(enabled)
    FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(enabled)
}
