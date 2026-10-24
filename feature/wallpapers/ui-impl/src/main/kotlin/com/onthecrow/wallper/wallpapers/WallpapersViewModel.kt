package com.onthecrow.wallper.wallpapers

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.blackhole.BlackHoleSettingsDestination
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.BuiltInPreviews
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Destination
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.EditorDestination
import com.onthecrow.wallper.editor.domain.IsMediaSupportedUseCase
import com.onthecrow.wallper.settings.SettingsDestination
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.domain.ActivateWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.DeleteWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpapersUseCase
import com.onthecrow.wallper.wallpapers.mapper.toItem
import com.onthecrow.wallper.wallpapers.model.WallpaperItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class WallpapersViewModel @Inject constructor(
    reducer: WallpapersReducer,
    observeWallpapersUseCase: ObserveWallpapersUseCase,
    private val activateWallpaperUseCase: ActivateWallpaperUseCase,
    private val deleteWallpaperUseCase: DeleteWallpaperUseCase,
    private val isMediaSupportedUseCase: IsMediaSupportedUseCase,
    private val liveWallpaperController: LiveWallpaperController,
    private val builtInPreviews: BuiltInPreviews,
    private val appSettings: AppSettingsSource,
    private val navigator: Navigator,
    private val dispatchersProvider: DispatchersProvider,
) : BaseViewModel<WallpapersEvent, WallpapersState, WallpapersReducer>(WallpapersState(), reducer) {

    /** Built-ins whose previews are followed. Touched only from [eventFlow], on the main thread. */
    private val followedPreviews = mutableSetOf<BuiltInWallpaper>()

    init {
        eventFlow.onEach { event ->
            when (event) {
                is WallpapersEvent.OnWallpaperClick -> activateWallpaper(event.id)
                is WallpapersEvent.OnEditClick -> navigator.navigate(EditorDestination.Edit(event.id))
                is WallpapersEvent.OnDeleteConfirm -> deleteWallpaper(event.id)
                is WallpapersEvent.OnBuiltInSettingsClick -> navigator.navigate(event.wallpaper.settingsDestination())
                is WallpapersEvent.OnWallpapersChanged -> event.items.mapNotNull { it.builtIn }.forEach(::followPreview)
                is WallpapersEvent.OnMediaPicked -> openEditor(event.uri)
                WallpapersEvent.OnSettingsClick -> navigator.navigate(SettingsDestination)
                else -> Unit
            }
        }.launchIn(viewModelScope)

        combine(observeWallpapersUseCase(), appSettings.settings.map { it.showBuiltInWallpapers }) { wallpapers, showsBuiltIns ->
            wallpapers.map { it.toItem() }.filter { item -> item.isListed(showsBuiltIns) }
        }
            .distinctUntilChanged()
            .onEach { items -> onEvent(WallpapersEvent.OnWallpapersChanged(items)) }
            .catch { onEvent(WallpapersEvent.OnWallpapersLoadFailed(it.message.orEmpty())) }
            .flowOn(dispatchersProvider.io)
            .launchIn(viewModelScope)
    }

    /** A built-in this device can't draw never is; hidden built-ins are, while one is the active wallpaper. */
    private fun WallpaperItem.isListed(showsBuiltIns: Boolean): Boolean {
        val builtIn = builtIn ?: return true
        return liveWallpaperController.isSupported(builtIn) && (showsBuiltIns || isActive)
    }

    private fun openEditor(uri: String) {
        viewModelScope.launch(dispatchersProvider.io) {
            if (isMediaSupportedUseCase(uri)) {
                navigator.navigate(EditorDestination.New(uri))
            } else {
                onEvent(WallpapersEvent.OnUnsupportedMediaPicked)
            }
        }
    }

    private fun activateWallpaper(id: Int) {
        viewModelScope.launch(dispatchersProvider.io) {
            activateWallpaperUseCase(id)
        }
    }

    private fun deleteWallpaper(id: Int) {
        viewModelScope.launch(dispatchersProvider.io) {
            deleteWallpaperUseCase(id)
        }
    }

    /**
     * Once per built-in, for as long as the list lives: a new picture comes after its settings change.
     * The first render may take seconds; the card shows a placeholder until then.
     */
    private fun followPreview(wallpaper: BuiltInWallpaper) {
        if (!followedPreviews.add(wallpaper)) return
        builtInPreviews.previews(wallpaper)
            .onEach { path -> onEvent(WallpapersEvent.OnBuiltInPreviewLoaded(wallpaper, path)) }
            .flowOn(dispatchersProvider.io)
            .launchIn(viewModelScope)
    }

    private fun BuiltInWallpaper.settingsDestination(): Destination = when (this) {
        BuiltInWallpaper.BLACK_HOLE -> BlackHoleSettingsDestination
    }
}
