package com.onthecrow.wallper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WallperApplication : Application() {

    @Inject
    lateinit var initManager: InitManager

    override fun onCreate() {
        super.onCreate()

        // TODO temp solution for testing, remove when file selection will be done
        initManager.populateDbIfNeeded()
    }
}