package com.onthecrow.wallper.blackhole.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onthecrow.wallper.blackhole.BlackHoleDebugDestination
import com.onthecrow.wallper.blackhole.BlackHoleDebugScreen
import com.onthecrow.wallper.blackhole.BlackHoleDebugViewModel
import com.onthecrow.wallper.blackhole.BlackHoleSettingsDestination
import com.onthecrow.wallper.blackhole.BlackHoleSettingsScreen
import com.onthecrow.wallper.blackhole.BlackHoleSettingsViewModel
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import com.onthecrow.wallper.blackhole.source.DebuggableBuild
import com.onthecrow.wallper.blackhole.source.LensTablesSource
import com.onthecrow.wallper.blackhole.source.RepositoryLensTablesSource
import com.onthecrow.wallper.blackhole.source.StoreBlackHoleSettingsSource
import com.onthecrow.wallper.core.navigation.FeatureEntry
import com.onthecrow.wallper.core.navigation.featureEntry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object BlackHoleUiModule {

    @Provides
    @IntoSet
    fun provideBlackHoleSettingsEntry(): FeatureEntry<*> = featureEntry<BlackHoleSettingsDestination> { _, modifier ->
        val viewModel = hiltViewModel<BlackHoleSettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        BlackHoleSettingsScreen(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }

    @Provides
    @IntoSet
    fun provideBlackHoleDebugEntry(): FeatureEntry<*> = featureEntry<BlackHoleDebugDestination> { _, modifier ->
        val viewModel = hiltViewModel<BlackHoleDebugViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        BlackHoleDebugScreen(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }

    @Provides
    fun provideSettingsSource(source: StoreBlackHoleSettingsSource): BlackHoleSettingsSource = source

    @Provides
    fun provideLensTablesSource(source: RepositoryLensTablesSource): LensTablesSource = source

    @Provides
    fun provideDebuggableBuild(@ApplicationContext context: Context): DebuggableBuild = DebuggableBuild {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
