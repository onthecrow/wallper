package com.onthecrow.wallper.editor.di

import com.onthecrow.wallper.editor.CreateWallpaperUseCaseImpl
import com.onthecrow.wallper.editor.DiscardPreparedMediaUseCaseImpl
import com.onthecrow.wallper.editor.IsMediaSupportedUseCaseImpl
import com.onthecrow.wallper.editor.PrepareMediaUseCaseImpl
import com.onthecrow.wallper.editor.domain.CreateWallpaperUseCase
import com.onthecrow.wallper.editor.domain.DiscardPreparedMediaUseCase
import com.onthecrow.wallper.editor.domain.IsMediaSupportedUseCase
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.export.TransformerVideoExporter
import com.onthecrow.wallper.editor.export.VideoExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class EditorLogicModule {

    @Binds
    abstract fun bindIsMediaSupported(impl: IsMediaSupportedUseCaseImpl): IsMediaSupportedUseCase

    @Binds
    abstract fun bindPrepareMedia(impl: PrepareMediaUseCaseImpl): PrepareMediaUseCase

    @Binds
    abstract fun bindCreateWallpaper(impl: CreateWallpaperUseCaseImpl): CreateWallpaperUseCase

    @Binds
    abstract fun bindDiscardPreparedMedia(impl: DiscardPreparedMediaUseCaseImpl): DiscardPreparedMediaUseCase

    @Binds
    abstract fun bindVideoExporter(impl: TransformerVideoExporter): VideoExporter
}
