package com.onthecrow.wallper.settings.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.onthecrow.wallper.core.engine.WallpaperSettingsSource
import com.onthecrow.wallper.settings.AppSettingsStore
import com.onthecrow.wallper.settings.CreateReportUseCaseImpl
import com.onthecrow.wallper.settings.WallpaperSettingsSourceImpl
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.settings.domain.CreateReportUseCase
import com.onthecrow.wallper.settings.domain.DebugBuild
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsLogicModule {

    @Binds
    abstract fun bindAppSettingsSource(impl: AppSettingsStore): AppSettingsSource

    @Binds
    abstract fun bindWallpaperSettingsSource(impl: WallpaperSettingsSourceImpl): WallpaperSettingsSource

    @Binds
    abstract fun bindCreateReport(impl: CreateReportUseCaseImpl): CreateReportUseCase

    companion object {
        @Provides
        fun provideDebugBuild(@ApplicationContext context: Context): DebugBuild = DebugBuild {
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        }
    }
}
