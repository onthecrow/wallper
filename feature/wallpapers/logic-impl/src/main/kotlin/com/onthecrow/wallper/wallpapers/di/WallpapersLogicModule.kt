package com.onthecrow.wallper.wallpapers.di

import android.content.Context
import com.onthecrow.wallper.core.engine.ActiveWallpaperSource
import com.onthecrow.wallper.wallpapers.ActivateWallpaperUseCaseImpl
import com.onthecrow.wallper.wallpapers.ActiveWallpaperSourceImpl
import com.onthecrow.wallper.wallpapers.DeleteWallpaperUseCaseImpl
import com.onthecrow.wallper.wallpapers.ObserveWallpapersUseCaseImpl
import com.onthecrow.wallper.wallpapers.SaveWallpaperUseCaseImpl
import com.onthecrow.wallper.wallpapers.data.database.WallpaperDao
import com.onthecrow.wallper.wallpapers.data.database.WallpapersDatabase
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.DeleteWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpapersUseCase
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WallpapersLogicModule {

    @Binds
    abstract fun bindObserveWallpapers(impl: ObserveWallpapersUseCaseImpl): ObserveWallpapersUseCase

    @Binds
    abstract fun bindActivateWallpaper(impl: ActivateWallpaperUseCaseImpl): ActivateWallpaperUseCase

    @Binds
    abstract fun bindDeleteWallpaper(impl: DeleteWallpaperUseCaseImpl): DeleteWallpaperUseCase

    @Binds
    abstract fun bindSaveWallpaper(impl: SaveWallpaperUseCaseImpl): SaveWallpaperUseCase

    @Binds
    abstract fun bindActiveWallpaperSource(impl: ActiveWallpaperSourceImpl): ActiveWallpaperSource

    companion object {

        /** In device-protected storage: the wallpaper service reads it in direct boot. */
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): WallpapersDatabase =
            WallpapersDatabase.builder(context.createDeviceProtectedStorageContext()).build()

        @Provides
        fun provideWallpaperDao(database: WallpapersDatabase): WallpaperDao = database.wallpaperDao()
    }
}
