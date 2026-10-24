package com.onthecrow.wallper.settings.di

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onthecrow.wallper.core.navigation.FeatureEntry
import com.onthecrow.wallper.core.navigation.featureEntry
import com.onthecrow.wallper.settings.SettingsDestination
import com.onthecrow.wallper.settings.SettingsScreen
import com.onthecrow.wallper.settings.SettingsViewModel
import com.onthecrow.wallper.settings.StorageDestination
import com.onthecrow.wallper.settings.storage.StorageScreen
import com.onthecrow.wallper.settings.storage.StorageViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object SettingsUiModule {

    @Provides
    @IntoSet
    fun provideSettingsEntry(): FeatureEntry<*> = featureEntry<SettingsDestination> { _, modifier ->
        val viewModel = hiltViewModel<SettingsViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        SettingsScreen(
            state = state,
            eventFlow = viewModel.eventFlow,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }

    @Provides
    @IntoSet
    fun provideStorageEntry(): FeatureEntry<*> = featureEntry<StorageDestination> { _, modifier ->
        val viewModel = hiltViewModel<StorageViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        StorageScreen(
            state = state,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }
}
