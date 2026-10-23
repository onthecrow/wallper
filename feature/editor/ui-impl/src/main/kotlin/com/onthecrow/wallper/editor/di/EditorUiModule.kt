package com.onthecrow.wallper.editor.di

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample

@Module
@InstallIn(SingletonComponent::class)
internal object EditorUiModule {

    @OptIn(FlowPreview::class)
    @Provides
    @IntoSet
    fun provideEditorEntry(): FeatureEntry<*> = featureEntry<EditorDestination> { destination, modifier ->
        val viewModel = hiltViewModel<EditorViewModel, EditorViewModel.Factory>(
            creationCallback = { factory -> factory.create(destination) },
        )
        val state by viewModel.state.collectAsStateWithLifecycle()
        val sampledState = remember(viewModel) { viewModel.state.sample(PLAYER_SEEK_SAMPLE_MS) }
        // Until the first sample arrives, the player starts from the current state.
        val playerState by sampledState.collectAsStateWithLifecycle(initialValue = state)
        EditorScreen(
            state = state,
            playerState = playerState,
            onEvent = viewModel::onEvent,
            modifier = modifier,
        )
    }

    private const val PLAYER_SEEK_SAMPLE_MS = 300L
}
