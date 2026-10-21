package com.onthecrow.wallper.core.navigation.impl.di

import com.onthecrow.wallper.core.navigation.FeatureEntry
import com.onthecrow.wallper.core.navigation.NavigationProvider
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.core.navigation.impl.NavigationProviderImpl
import com.onthecrow.wallper.core.navigation.impl.NavigatorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NavigationModule {

    @Binds
    abstract fun bindNavigator(impl: NavigatorImpl): Navigator

    @Binds
    abstract fun bindNavigationProvider(impl: NavigationProviderImpl): NavigationProvider

    @Multibinds
    abstract fun featureEntries(): Set<FeatureEntry<*>>
}
