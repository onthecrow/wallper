package com.onthecrow.wallper.core.storage.di

import com.onthecrow.wallper.core.storage.MediaStorage
import com.onthecrow.wallper.core.storage.MediaStorageImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StorageModule {

    @Binds
    abstract fun bindMediaStorage(impl: MediaStorageImpl): MediaStorage
}
