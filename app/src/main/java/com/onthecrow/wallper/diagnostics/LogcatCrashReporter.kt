package com.onthecrow.wallper.diagnostics

import android.annotation.SuppressLint
import android.util.Log
import com.onthecrow.wallper.core.diagnostics.CrashReporter

/**
 * The release build's reports in logcat, beside Crashlytics: `adb logcat -s WallperReport` and the in-app
 * report show them, also in direct boot, before Crashlytics starts. Breadcrumbs stay out.
 */
@SuppressLint("LogNotTimber") // Timber feeds this reporter: logging back through it would loop.
internal class LogcatCrashReporter : CrashReporter {
    override fun setKey(key: String, value: String) {
        Log.i(TAG, "$key = $value")
    }

    override fun recordNonFatal(message: String, error: Throwable?) {
        Log.e(TAG, message, error)
    }

    override fun log(message: String) = Unit

    private companion object {
        const val TAG = "WallperReport"
    }
}
