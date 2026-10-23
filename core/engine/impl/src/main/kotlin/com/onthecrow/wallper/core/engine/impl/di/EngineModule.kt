package com.onthecrow.wallper.core.engine.impl.di

import com.onthecrow.wallper.core.engine.BuiltInPreviews
import com.onthecrow.wallper.core.engine.BuiltInWallpaperProvider
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.engine.impl.BuiltInPreviewsImpl
import com.onthecrow.wallper.core.engine.impl.LiveWallpaperControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EngineModule {

    @Binds
    abstract fun bindLiveWallpaperController(impl: LiveWallpaperControllerImpl): LiveWallpaperController

    @Binds
    abstract fun bindBuiltInPreviews(impl: BuiltInPreviewsImpl): BuiltInPreviews

    /** Features add theirs with `@IntoSet`; the set may be empty. */
    @Multibinds
    abstract fun builtInWallpaperProviders(): Set<BuiltInWallpaperProvider>
}
