package com.onthecrow.wallper.di

import android.content.Context
import com.onthecrow.wallper.crop.FallbackVideoCropper
import com.onthecrow.wallper.crop.FfmpegVideoCropperImpl
import com.onthecrow.wallper.crop.MainVideoCropper
import com.onthecrow.wallper.crop.TransformerVideoCropperImpl
import com.onthecrow.wallper.crop.VideoCropper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class DomainModule {

    @Provides
    @FallbackVideoCropper
    fun provideFallbackVideoCropper(): VideoCropper {
        return FfmpegVideoCropperImpl()
    }

    @Provides
    @MainVideoCropper
    fun provideMainVideoCropper(@ApplicationContext context: Context): VideoCropper {
        return TransformerVideoCropperImpl(context)
    }
}