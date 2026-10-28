package com.onthecrow.wallper.diagnostics

import android.content.Context
import android.os.UserManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.onthecrow.wallper.core.diagnostics.CrashReporter
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Release builds: reports go to Crashlytics and to logcat, which the in-app report reads. Crashlytics keeps its
 * files in credential-encrypted storage, so Firebase starts it only after the first unlock: in direct boot the
 * reports reach logcat alone and the keys wait. A build without google-services.json has no Firebase app.
 */
internal class FirebaseCrashReporter(context: Context) : CrashReporter {

    private val context = context.applicationContext
    private val userManager = context.getSystemService(UserManager::class.java)
    private val logcat = LogcatCrashReporter()
    private val keys = ConcurrentHashMap<String, String>()

    @Volatile
    private var crashlytics: FirebaseCrashlytics? = null

    override fun setKey(key: String, value: String) {
        logcat.setKey(key, value)
        keys[key] = value
        crashlytics()?.setCustomKey(key, value)
    }

    override fun recordNonFatal(message: String, error: Throwable?) {
        logcat.recordNonFatal(message, error)
        val crashlytics = crashlytics() ?: return
        if (error != null) crashlytics.log(message)
        crashlytics.recordException(error ?: LoggedError(message))
    }

    override fun log(message: String) {
        crashlytics()?.log(message)
    }

    private fun crashlytics(): FirebaseCrashlytics? {
        crashlytics?.let { return it }
        if (!userManager.isUserUnlocked || FirebaseApp.getApps(context).isEmpty()) return null
        return FirebaseCrashlytics.getInstance().also { instance ->
            keys.forEach(instance::setCustomKey)
            crashlytics = instance
        }
    }
}

/**
 * An error logged without an exception. Its trace starts where it was logged, not in Timber, so Crashlytics
 * tells such reports apart.
 */
private class LoggedError(message: String) : Exception(message) {
    init {
        stackTrace = stackTrace.dropWhile { it.className in REPORTING_CLASSES }.toTypedArray()
    }
}

/** The classes a report passes on its way to Crashlytics. By their names at run time: R8 renames them. */
private val REPORTING_CLASSES = listOf(
    LoggedError::class.java,
    FirebaseCrashReporter::class.java,
    ReportingTree::class.java,
    Timber::class.java,
    Timber.Forest::class.java,
    Timber.Tree::class.java,
).mapTo(HashSet()) { it.name }
