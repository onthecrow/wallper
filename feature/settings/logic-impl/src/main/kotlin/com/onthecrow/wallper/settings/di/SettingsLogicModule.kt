package com.onthecrow.wallper.settings.di

import com.onthecrow.wallper.core.engine.VideoWallpaperSettingsSource
import com.onthecrow.wallper.settings.AppSettingsStore
import com.onthecrow.wallper.settings.CreateReportUseCaseImpl
import com.onthecrow.wallper.settings.VideoWallpaperSettingsSourceImpl
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.settings.domain.CreateReportUseCase
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsLogicModule {

    @Binds
    abstract fun bindAppSettingsSource(impl: AppSettingsStore): AppSettingsSource

    @Binds
    abstract fun bindVideoWallpaperSettingsSource(impl: VideoWallpaperSettingsSourceImpl): VideoWallpaperSettingsSource

    @Binds
    abstract fun bindCreateReport(impl: CreateReportUseCaseImpl): CreateReportUseCase
}
