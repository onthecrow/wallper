package com.onthecrow.wallper.wallpapers.di

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onthecrow.wallper.core.navigation.FeatureEntry
import com.onthecrow.wallper.core.navigation.featureEntry
import com.onthecrow.wallper.wallpapers.WallpapersDestination
import com.onthecrow.wallper.wallpapers.WallpapersScreen
import com.onthecrow.wallper.wallpapers.WallpapersViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object WallpapersUiModule {

    @Provides
    @IntoSet
    fun provideWallpapersEntry(): FeatureEntry<*> = featureEntry<WallpapersDestination> { _, modifier ->
        val viewModel = hiltViewModel<WallpapersViewModel>()
        val state by viewModel.state.collectAsStateWithLifecycle()
        WallpapersScreen(
            state = state,
            eventFlow = viewModel.eventFlow,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }
}
