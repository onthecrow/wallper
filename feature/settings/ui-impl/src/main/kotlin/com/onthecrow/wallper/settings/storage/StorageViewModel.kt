package com.onthecrow.wallper.settings.storage

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.EditorDestination
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpaperStorageUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.domain.model.WallpaperStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

/** Optimising opens the editor on the wallpaper, switched to optimised: the user still sees it and saves. */
@HiltViewModel
internal class StorageViewModel @Inject constructor(
    reducer: StorageReducer,
    observeWallpaperStorageUseCase: ObserveWallpaperStorageUseCase,
    private val navigator: Navigator,
    dispatchersProvider: DispatchersProvider,
) : BaseViewModel<StorageEvent, StorageState, StorageReducer>(StorageState(), reducer) {

    init {
        eventFlow.onEach { event ->
            when (event) {
                StorageEvent.OnBackClick -> navigator.navigateBack()
                is StorageEvent.OnOptimiseClick -> navigator.navigate(EditorDestination.Edit(event.id, optimise = true))
                else -> Unit
            }
        }.launchIn(viewModelScope)

        observeWallpaperStorageUseCase()
            .map { wallpapers -> wallpapers.mapNotNull { it.toItem() } }
            .flowOn(dispatchersProvider.io)
            .onEach { items -> onEvent(StorageEvent.OnItemsChanged(items)) }
            .catch { Timber.e(it, "Can't count the wallpapers' storage") }
            .launchIn(viewModelScope)
    }

    private fun WallpaperStorage.toItem(): StorageItem? = when (val wallpaper = wallpaper) {
        is Wallpaper.Media -> if (wallpaper.type == MediaType.VIDEO) {
            StorageItem(wallpaper.id, wallpaper.thumbnailPath, StorageItem.Kind.ORIGINAL_VIDEO, bytes)
        } else {
            StorageItem(wallpaper.id, wallpaper.originalPath, StorageItem.Kind.PICTURE, bytes)
        }
        is Wallpaper.ProcessedVideo -> StorageItem(wallpaper.id, wallpaper.thumbnailPath, StorageItem.Kind.OPTIMISED_VIDEO, bytes)
        is Wallpaper.BuiltIn -> null
    }
}
