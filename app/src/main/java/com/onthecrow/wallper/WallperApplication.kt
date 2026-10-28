package com.onthecrow.wallper

import android.app.Application
import android.os.Build
import com.onthecrow.wallper.diagnostics.ExitReasons
import com.onthecrow.wallper.diagnostics.ReportingTree
import com.onthecrow.wallper.diagnostics.installDiagnostics
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import kotlin.concurrent.thread

@HiltAndroidApp
class WallperApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        installDiagnostics()
        Timber.plant(ReportingTree())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            thread(name = "ExitReasons") { ExitReasons.reportNew(this) }
        }
    }
}
