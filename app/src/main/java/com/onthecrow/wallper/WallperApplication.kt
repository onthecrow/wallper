package com.onthecrow.wallper

import android.app.Application
import android.os.Build
import com.onthecrow.wallper.core.coroutines.ApplicationScopeProvider
import com.onthecrow.wallper.diagnostics.ExitReasons
import com.onthecrow.wallper.diagnostics.ReportingTree
import com.onthecrow.wallper.diagnostics.installDiagnostics
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class WallperApplication : Application() {

    @Inject
    lateinit var appSettings: AppSettingsSource

    @Inject
    lateinit var scopeProvider: ApplicationScopeProvider

    override fun onCreate() {
        super.onCreate()

        installDiagnostics(appSettings, scopeProvider.applicationScope)
        Timber.plant(ReportingTree())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ExitReasons.reportOnceUnlocked(this)
        }
    }
}
