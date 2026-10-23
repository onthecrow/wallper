package com.onthecrow.wallper

import android.app.Application
import android.os.Build
import com.onthecrow.wallper.core.diagnostics.Diagnostics
import com.onthecrow.wallper.diagnostics.DebugFileTree
import com.onthecrow.wallper.diagnostics.ExitReasons
import com.onthecrow.wallper.diagnostics.LogcatCrashReporter
import com.onthecrow.wallper.diagnostics.ReportingTree
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import kotlin.concurrent.thread

@HiltAndroidApp
class WallperApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(DebugFileTree(this))
        } else {
            Diagnostics.reporter = LogcatCrashReporter()
        }
        Timber.plant(ReportingTree())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            thread(name = "ExitReasons") { ExitReasons.reportNew(this) }
        }
    }
}
