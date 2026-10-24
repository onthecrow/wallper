package com.onthecrow.wallper.settings

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.core.coroutines.DispatchersProvider
import com.onthecrow.wallper.core.engine.LiveWallpaperController
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import com.onthecrow.wallper.settings.domain.AppSettings
import com.onthecrow.wallper.settings.domain.AppSettingsSource
import com.onthecrow.wallper.settings.domain.CreateReportUseCase
import com.onthecrow.wallper.wallpapers.domain.ObserveWallpaperStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Asks the system whether Wallper is the wallpaper as the screen opens, whenever it returns (from the
 * system picker, say) and whenever the system reports a new wallpaper while the screen is open. Every
 * other setting is written to [AppSettingsSource] and comes back from it.
 */
@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    reducer: SettingsReducer,
    private val liveWallpaperController: LiveWallpaperController,
    private val appSettings: AppSettingsSource,
    observeWallpaperStorageUseCase: ObserveWallpaperStorageUseCase,
    private val createReportUseCase: CreateReportUseCase,
    private val navigator: Navigator,
    dispatchersProvider: DispatchersProvider,
) : BaseViewModel<SettingsEvent, SettingsState, SettingsReducer>(SettingsState(), reducer) {

    private val checkRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One report at a time; touched only on the main thread. */
    private var reportJob: Job? = null

    init {
        eventFlow.onEach { event ->
            when (event) {
                SettingsEvent.OnResume -> checkRequests.tryEmit(Unit)
                SettingsEvent.OnSetWallpaperClick -> onEvent(
                    SettingsEvent.OpenLiveWallpaperChooser(liveWallpaperController.createSetWallpaperIntent())
                )
                SettingsEvent.OnBackClick -> navigator.navigateBack()
                SettingsEvent.OnStorageClick -> navigator.navigate(StorageDestination)
                SettingsEvent.OnSendReportClick -> sendReport()
                is SettingsEvent.OnPlaybackSelect -> update { it.copy(videoPlayback = event.playback) }
                is SettingsEvent.OnSaveBatteryChange -> update { it.copy(pauseVideoToSaveBattery = event.enabled) }
                is SettingsEvent.OnScrollWithPagesChange -> update { it.copy(scrollVideoWithPages = event.enabled) }
                is SettingsEvent.OnActivateNewWallpapersChange -> update { it.copy(activateNewWallpapers = event.enabled) }
                is SettingsEvent.OnShowBuiltInWallpapersChange -> update { it.copy(showBuiltInWallpapers = event.enabled) }
                is SettingsEvent.OnThemeSelect -> update { it.copy(theme = event.theme) }
                is SettingsEvent.OnDynamicColorChange -> update { it.copy(dynamicColor = event.enabled) }
                else -> Unit
            }
        }.launchIn(viewModelScope)

        appSettings.settings
            .onEach { settings -> onEvent(SettingsEvent.OnSettingsChanged(settings)) }
            .launchIn(viewModelScope)

        observeWallpaperStorageUseCase()
            .map { wallpapers -> wallpapers.sumOf { it.bytes } }
            .distinctUntilChanged()
            .flowOn(dispatchersProvider.io)
            .onEach { bytes -> onEvent(SettingsEvent.OnStorageCounted(bytes)) }
            .catch { Timber.e(it, "Can't count the wallpapers' storage") }
            .launchIn(viewModelScope)

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

    private fun update(transform: (AppSettings) -> AppSettings) = appSettings.update(transform)

    private fun sendReport() {
        if (reportJob?.isActive == true) return
        reportJob = viewModelScope.launch {
            try {
                onEvent(SettingsEvent.ShareReport(createReportUseCase()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Can't create a report")
                onEvent(SettingsEvent.OnReportFailed)
            }
        }
    }
}
