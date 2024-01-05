package com.onthecrow.wallper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber.*
import timber.log.Timber.Forest.plant
import javax.inject.Inject


@HiltAndroidApp
class WallperApplication : Application() {

    @Inject
    lateinit var initManager: InitManager

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            plant(DebugTree())
        }

        // TODO temp solution for testing, remove when file selection will be done
//        initManager.populateDbIfNeeded()
    }
}