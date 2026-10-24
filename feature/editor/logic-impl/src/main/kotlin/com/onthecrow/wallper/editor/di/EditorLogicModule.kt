package com.onthecrow.wallper.editor.di

import com.onthecrow.wallper.editor.DiscardPreparedMediaUseCaseImpl
import com.onthecrow.wallper.editor.IsMediaSupportedUseCaseImpl
import com.onthecrow.wallper.editor.PrepareMediaUseCaseImpl
import com.onthecrow.wallper.editor.PrepareSavedMediaUseCaseImpl
import com.onthecrow.wallper.editor.ProcessingInfoMemoryImpl
import com.onthecrow.wallper.editor.SaveWallpaperUseCaseImpl
import com.onthecrow.wallper.editor.domain.DiscardPreparedMediaUseCase
import com.onthecrow.wallper.editor.domain.IsMediaSupportedUseCase
import com.onthecrow.wallper.editor.domain.PrepareMediaUseCase
import com.onthecrow.wallper.editor.domain.PrepareSavedMediaUseCase
import com.onthecrow.wallper.editor.domain.ProcessingInfoMemory
import com.onthecrow.wallper.editor.domain.SaveWallpaperUseCase
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
    abstract fun bindPrepareSavedMedia(impl: PrepareSavedMediaUseCaseImpl): PrepareSavedMediaUseCase

    @Binds
    abstract fun bindSaveWallpaper(impl: SaveWallpaperUseCaseImpl): SaveWallpaperUseCase

    @Binds
    abstract fun bindProcessingInfoMemory(impl: ProcessingInfoMemoryImpl): ProcessingInfoMemory

    @Binds
    abstract fun bindDiscardPreparedMedia(impl: DiscardPreparedMediaUseCaseImpl): DiscardPreparedMediaUseCase

    @Binds
    abstract fun bindVideoExporter(impl: TransformerVideoExporter): VideoExporter
}
