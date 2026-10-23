package com.onthecrow.wallper.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import timber.log.Timber

/**
 * Reports how earlier runs of the process ended when that was a crash or an ANR (API 30+): an ANR of the
 * wallpaper service is seen nowhere else. The newest exit reported is kept in device-protected prefs.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal object ExitReasons {

    private val REPORTED = setOf(
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    )
    private const val PREFS = "diagnostics"
    private const val KEY_LAST_EXIT = "last_reported_exit"
    private const val MAX_EXITS = 16

    /** Binder call and disk: off the main thread. */
    fun reportNew(context: Context) {
        val prefs = context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastReported = prefs.getLong(KEY_LAST_EXIT, 0)
        val exits = context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(null, 0, MAX_EXITS)
            .orEmpty()
        exits.filter { it.timestamp > lastReported && it.reason in REPORTED }
            .sortedBy { it.timestamp }
            .forEach { exit ->
                Timber.e(
                    "Process %s ended: %s, %s (importance %d)",
                    exit.processName, exit.reasonName(), exit.description, exit.importance,
                )
            }
        val newest = exits.maxOfOrNull { it.timestamp } ?: return
        if (newest > lastReported) prefs.edit { putLong(KEY_LAST_EXIT, newest) }
    }

    private fun ApplicationExitInfo.reasonName(): String = when (reason) {
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_CRASH -> "crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
        else -> "reason $reason"
    }
}
