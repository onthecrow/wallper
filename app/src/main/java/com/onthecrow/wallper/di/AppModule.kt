package com.onthecrow.wallper.di

import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.impl.StartDestination
import com.onthecrow.wallper.wallpapers.WallpapersDestination
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @StartDestination
    fun provideStartDestination(): Destination = WallpapersDestination
}
