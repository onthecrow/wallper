package com.onthecrow.wallper.blackhole.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onthecrow.wallper.blackhole.BlackHoleDebugDestination
import com.onthecrow.wallper.blackhole.BlackHoleDebugScreen
import com.onthecrow.wallper.blackhole.BlackHoleDebugViewModel
import com.onthecrow.wallper.blackhole.BlackHoleSettingsDestination
import com.onthecrow.wallper.blackhole.BlackHoleSettingsScreen
import com.onthecrow.wallper.blackhole.BlackHoleSettingsViewModel
import com.onthecrow.wallper.blackhole.gl.LocalBlackHoleRenderers
import com.onthecrow.wallper.blackhole.renderer.BlackHoleRendererFactory
import com.onthecrow.wallper.blackhole.source.DebuggableBuild
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
    fun provideBlackHoleSettingsEntry(renderers: BlackHoleRendererFactory): FeatureEntry<*> =
        featureEntry<BlackHoleSettingsDestination> { _, modifier ->
            val viewModel = hiltViewModel<BlackHoleSettingsViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalBlackHoleRenderers provides renderers) {
                BlackHoleSettingsScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    modifier = modifier,
                )
            }
        }

    @Provides
    @IntoSet
    fun provideBlackHoleDebugEntry(renderers: BlackHoleRendererFactory): FeatureEntry<*> =
        featureEntry<BlackHoleDebugDestination> { _, modifier ->
            val viewModel = hiltViewModel<BlackHoleDebugViewModel>()
            val state by viewModel.state.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalBlackHoleRenderers provides renderers) {
                BlackHoleDebugScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    modifier = modifier,
                )
            }
        }

    @Provides
    fun provideDebuggableBuild(@ApplicationContext context: Context): DebuggableBuild = DebuggableBuild {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
