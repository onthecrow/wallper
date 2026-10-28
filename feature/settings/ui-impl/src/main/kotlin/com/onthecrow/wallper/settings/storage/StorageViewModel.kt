package com.onthecrow.wallper.settings.storage

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.editor.EditorDestination
import com.onthecrow.wallper.wallpapers.domain.DeleteWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpaperStorageUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.domain.model.WallpaperStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Edit and delete act as on the list's card; an original video is optimised in its editor. */
@HiltViewModel
internal class StorageViewModel @Inject constructor(
    reducer: StorageReducer,
    observeWallpaperStorageUseCase: ObserveWallpaperStorageUseCase,
    private val deleteWallpaperUseCase: DeleteWallpaperUseCase,
    private val navigator: Navigator,
    private val dispatchersProvider: DispatchersProvider,
) : BaseViewModel<StorageEvent, StorageState, StorageReducer>(StorageState(), reducer) {

    init {
        eventFlow.onEach { event ->
            when (event) {
                StorageEvent.OnBackClick -> navigator.navigateBack()
                is StorageEvent.OnEditClick -> navigator.navigate(EditorDestination.Edit(event.id))
                is StorageEvent.OnDeleteConfirm -> deleteWallpaper(event.id)
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

    // The list follows the database: the row goes once the wallpaper is gone.
    private fun deleteWallpaper(id: Int) {
        viewModelScope.launch(dispatchersProvider.io) {
            deleteWallpaperUseCase(id)
        }
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
