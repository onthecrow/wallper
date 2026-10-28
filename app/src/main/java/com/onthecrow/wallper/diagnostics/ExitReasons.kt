package com.onthecrow.wallper.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import timber.log.Timber
import kotlin.concurrent.thread

/**
 * Reports how earlier runs of the process ended when that was a crash or an ANR (API 30+): Crashlytics
 * doesn't see native crashes, and none before the first unlock. The newest exit reported is kept in
 * device-protected prefs.
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

    /**
     * Reports now, or once the user unlocks. In direct boot Crashlytics isn't running: an exit reported then
     * would reach logcat alone and still count as reported, so a crash before the first unlock was lost.
     */
    fun reportOnceUnlocked(context: Context) {
        val appContext = context.applicationContext
        val userManager = appContext.getSystemService(UserManager::class.java)
        if (!userManager.isUserUnlocked) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    appContext.unregisterReceiver(this)
                    report(appContext)
                }
            }
            ContextCompat.registerReceiver(
                appContext, receiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            // The user may have unlocked before the receiver was there.
            if (!userManager.isUserUnlocked) return
            appContext.unregisterReceiver(receiver)
        }
        report(appContext)
    }

    /** Binder call and disk: off the main thread. */
    private fun report(context: Context) {
        thread(name = "ExitReasons") { reportNew(context) }
    }

    private fun reportNew(context: Context) {
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
