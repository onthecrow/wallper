package com.onthecrow.wallper.blackhole.di

import android.content.Context
import com.onthecrow.wallper.blackhole.data.BlackHoleSettingsStore
import com.onthecrow.wallper.blackhole.data.LensTablesRepository
import com.onthecrow.wallper.blackhole.engine.BlackHoleWallpaperProvider
import com.onthecrow.wallper.blackhole.renderer.BlackHoleRenderer
import com.onthecrow.wallper.blackhole.renderer.BlackHoleRendererFactory
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import com.onthecrow.wallper.blackhole.source.LensTablesSource
import com.onthecrow.wallper.core.engine.BuiltInWallpaperProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BlackHoleLogicModule {

    @Binds
    @IntoSet
    abstract fun bindWallpaperProvider(impl: BlackHoleWallpaperProvider): BuiltInWallpaperProvider

    @Binds
    abstract fun bindSettingsSource(impl: BlackHoleSettingsStore): BlackHoleSettingsSource

    @Binds
    abstract fun bindLensTablesSource(impl: LensTablesRepository): LensTablesSource

    companion object {
        @Provides
        fun provideRendererFactory(@ApplicationContext context: Context): BlackHoleRendererFactory =
            BlackHoleRendererFactory { BlackHoleRenderer(context.assets) }
    }
}
