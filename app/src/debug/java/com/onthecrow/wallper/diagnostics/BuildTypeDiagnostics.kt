package com.onthecrow.wallper.diagnostics

import android.app.Application
import timber.log.Timber

/** Debug builds log to logcat and a file and report nothing: Firebase isn't in them. */
internal fun Application.installDiagnostics() {
    Timber.plant(Timber.DebugTree())
    Timber.plant(DebugFileTree(this))
}
