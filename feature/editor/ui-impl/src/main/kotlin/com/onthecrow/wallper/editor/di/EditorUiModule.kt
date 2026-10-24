package com.onthecrow.wallper.editor.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onthecrow.wallper.core.navigation.FeatureEntry
import com.onthecrow.wallper.core.navigation.featureEntry
import com.onthecrow.wallper.editor.EditorDestination
import com.onthecrow.wallper.editor.EditorScreen
import com.onthecrow.wallper.editor.EditorViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal object EditorUiModule {

    @Provides
    @IntoSet
    fun provideNewWallpaperEntry(): FeatureEntry<*> = featureEntry<EditorDestination.New> { destination, modifier ->
        EditorEntry(destination, modifier)
    }

    @Provides
    @IntoSet
    fun provideEditWallpaperEntry(): FeatureEntry<*> = featureEntry<EditorDestination.Edit> { destination, modifier ->
        EditorEntry(destination, modifier)
    }
}

@Composable
private fun EditorEntry(destination: EditorDestination, modifier: Modifier) {
    val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(
        creationCallback = { factory -> factory.create(destination) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    EditorScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}
