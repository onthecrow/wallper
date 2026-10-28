package com.onthecrow.wallper.diagnostics

import android.app.Application
import com.onthecrow.wallper.core.diagnostics.Diagnostics

/** Release builds report to Crashlytics and logcat. Firebase starts itself (`FirebaseInitProvider`). */
internal fun Application.installDiagnostics() {
    Diagnostics.reporter = FirebaseCrashReporter(this)
}
