package com.onthecrow.wallper.wallpapers.group

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.BuiltInPreviews
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.wallpapers.WallpaperGroupDestination
import com.onthecrow.wallper.wallpapers.domain.GetWallpaperUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpapersUseCase
import com.onthecrow.wallper.wallpapers.domain.SaveWallpaperGroupUseCase
import com.onthecrow.wallper.wallpapers.domain.model.Wallpaper
import com.onthecrow.wallper.wallpapers.mapper.toItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel(assistedFactory = GroupEditorViewModel.Factory::class)
internal class GroupEditorViewModel @AssistedInject constructor(
    @Assisted private val destination: WallpaperGroupDestination,
    reducer: GroupEditorReducer,
    observeWallpapersUseCase: ObserveWallpapersUseCase,
    private val getWallpaperUseCase: GetWallpaperUseCase,
    private val saveWallpaperGroupUseCase: SaveWallpaperGroupUseCase,
    private val liveWallpaperController: LiveWallpaperController,
    private val builtInPreviews: BuiltInPreviews,
    appSettings: AppSettingsSource,
    private val navigator: Navigator,
    private val dispatchersProvider: DispatchersProvider,
) : BaseViewModel<GroupEditorEvent, GroupEditorState, GroupEditorReducer>(
    GroupEditorState.initial(destination.groupId),
    reducer,
) {

    @AssistedFactory
    interface Factory {
        fun create(destination: WallpaperGroupDestination): GroupEditorViewModel
    }

    /** Built-ins whose previews are followed. Touched only from [eventFlow], on the main thread. */
    private val followedPreviews = mutableSetOf<BuiltInWallpaper>()

    init {
        eventFlow.onEach { event ->
            when (event) {
                GroupEditorEvent.OnCloseClick -> navigator.navigateBack()
                is GroupEditorEvent.OnWallpapersChanged -> event.items.mapNotNull { it.builtIn }.forEach(::followPreview)
                else -> Unit
            }
        }.launchIn(viewModelScope)

        // Groups aren't members; built-ins this device can't draw aren't either.
        combine(observeWallpapersUseCase(), appSettings.settings.map { it.showBuiltInWallpapers }) { wallpapers, showsBuiltIns ->
            val items = wallpapers.filterNot { it is Wallpaper.Group }.map { it.toItem() }
                .filter { item -> item.builtIn?.let(liveWallpaperController::isSupported) ?: true }
            GroupEditorEvent.OnWallpapersChanged(items, showsBuiltIns)
        }
            .distinctUntilChanged()
            .onEach(::onEvent)
            .catch { Timber.e(it, "Can't read the wallpapers") }
            .flowOn(dispatchersProvider.io)
            .launchIn(viewModelScope)

        destination.groupId?.let(::loadGroup)

        // The reducer decides when a save starts, one at a time.
        state.map { it.save }.distinctUntilChanged().filterNotNull().onEach(::save).launchIn(viewModelScope)
    }

    /** A group that is gone (deleted meanwhile) has nothing to edit. */
    private fun loadGroup(id: Int) {
        viewModelScope.launch(dispatchersProvider.io) {
            val group = getWallpaperUseCase(id) as? Wallpaper.Group
            if (group == null) {
                navigator.navigateBack()
            } else {
                onEvent(GroupEditorEvent.OnGroupLoaded(group.members.map { it.id }, group.slideDuration))
            }
        }
    }

    private fun save(request: GroupSaveRequest) {
        viewModelScope.launch(dispatchersProvider.io) {
            try {
                saveWallpaperGroupUseCase(destination.groupId, request.memberIds, request.slideDuration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.e(error, "Can't save the group")
                onEvent(GroupEditorEvent.OnSaveFailed)
                return@launch
            }
            navigator.navigateBack()
        }
    }

    /** Once per built-in, for as long as the screen lives; the card shows a placeholder until then. */
    private fun followPreview(wallpaper: BuiltInWallpaper) {
        if (!followedPreviews.add(wallpaper)) return
        builtInPreviews.previews(wallpaper)
            .onEach { path -> onEvent(GroupEditorEvent.OnBuiltInPreviewLoaded(wallpaper, path)) }
            .flowOn(dispatchersProvider.io)
            .launchIn(viewModelScope)
    }
}
