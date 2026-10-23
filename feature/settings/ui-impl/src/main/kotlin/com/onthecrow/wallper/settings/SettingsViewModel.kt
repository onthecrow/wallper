package com.onthecrow.wallper.settings

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * Asks the system whether Wallper is the wallpaper as the screen opens, whenever it returns (from the
 * system picker, say) and whenever the system reports a new wallpaper while the screen is open.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    reducer: SettingsReducer,
    private val liveWallpaperController: LiveWallpaperController,
    private val navigator: Navigator,
    dispatchersProvider: DispatchersProvider,
) : BaseViewModel<SettingsEvent, SettingsState, SettingsReducer>(SettingsState(), reducer) {

    private val checkRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        eventFlow.onEach { event ->
            when (event) {
                SettingsEvent.OnResume -> checkRequests.tryEmit(Unit)
                SettingsEvent.OnSetWallpaperClick -> onEvent(
                    SettingsEvent.OpenLiveWallpaperChooser(liveWallpaperController.createSetWallpaperIntent())
                )
                SettingsEvent.OnBackClick -> navigator.navigateBack()
                else -> Unit
            }
        }.launchIn(viewModelScope)

        // One check at a time, so an older answer never lands after a newer one. The first one doesn't
        // wait for the screen's resume, which may come only after its enter transition.
        merge(checkRequests, liveWallpaperController.wallpaperChanges())
            .onStart { emit(Unit) }
            .conflate()
            .map { liveWallpaperController.isWallpaperSet() }
            .flowOn(dispatchersProvider.io)
            .onEach { isSet -> onEvent(SettingsEvent.OnWallpaperChecked(isSet)) }
            .launchIn(viewModelScope)
    }
}
