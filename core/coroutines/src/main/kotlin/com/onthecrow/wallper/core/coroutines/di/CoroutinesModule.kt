package com.onthecrow.wallper.core.coroutines.di

import com.onthecrow.wallper.core.coroutines.ApplicationScopeProvider
import com.onthecrow.wallper.core.coroutines.DefaultApplicationScopeProvider
import com.onthecrow.wallper.core.coroutines.DefaultDispatchersProvider
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CoroutinesModule {

    @Provides
    fun provideDispatchers(): DispatchersProvider = DefaultDispatchersProvider

    @Provides
    @Singleton
    fun provideApplicationScope(dispatchersProvider: DispatchersProvider): ApplicationScopeProvider =
        DefaultApplicationScopeProvider(dispatchersProvider)
}
