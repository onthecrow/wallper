package com.onthecrow.wallper.di

import android.content.Context
import androidx.room.Room
import com.onthecrow.wallper.data.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val APP_DATABASE_NAME = "wallper-db"

@Module
@InstallIn(SingletonComponent::class)
class DataModule {

    @Provides
    @Singleton
    fun provideDB(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.createDeviceProtectedStorageContext(),
            AppDatabase::class.java, APP_DATABASE_NAME
        ).build()
    }
}