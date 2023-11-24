package com.onthecrow.wallper

import android.app.Application
import androidx.room.Room
import com.onthecrow.wallper.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class WallperApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "wallper-db"
        ).build()

        // TODO temp solution for testing, remove when file selection will be done
        MainScope().launch(Dispatchers.IO) {
            InitManager.populateDbIfNeeded(this@WallperApplication, db!!.wallpaperDao())
        }
    }

    companion object {
        var db: AppDatabase? = null
            private set
    }
}