package com.onthecrow.wallper.blackhole

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import com.onthecrow.wallper.blackhole.source.DebuggableBuild
import com.onthecrow.wallper.blackhole.source.LensTablesSource
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettings
import com.onthecrow.wallper.core.mvi.BaseViewModel
import com.onthecrow.wallper.core.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.random.Random

/**
 * Writes the settings to the store and keeps the live preview fed: it loads the lens tables whenever
 * the stored settings ask for other ones (a new distance or quality).
 */
@HiltViewModel
internal class BlackHoleSettingsViewModel @Inject constructor(
    reducer: BlackHoleSettingsReducer,
    private val settingsSource: BlackHoleSettingsSource,
    private val tablesSource: LensTablesSource,
    private val navigator: Navigator,
    debuggableBuild: DebuggableBuild,
) : BaseViewModel<BlackHoleSettingsEvent, BlackHoleSettingsState, BlackHoleSettingsReducer>(
    BlackHoleSettingsState(settings = settingsSource.settings.value, isDebugAvailable = debuggableBuild.isDebuggable()),
    reducer,
) {

    /** Touched only from [eventFlow] and [init], on the main thread. */
    private var requestedKey: LensTableKey? = null
    private var tablesJob: Job? = null

    init {
        eventFlow.onEach { event ->
            when (event) {
                is BlackHoleSettingsEvent.OnQualityChange -> save { copy(quality = event.quality) }
                is BlackHoleSettingsEvent.OnGyroToggle -> save { copy(gyroEnabled = event.enabled) }
                is BlackHoleSettingsEvent.OnLauncherToggle -> save { copy(launcherEnabled = event.enabled) }
                is BlackHoleSettingsEvent.OnOrbitToggle -> save { copy(orbitEnabled = event.enabled) }
                is BlackHoleSettingsEvent.OnZoomOutToggle -> save { copy(zoomOutEnabled = event.enabled) }
                is BlackHoleSettingsEvent.OnDopplerToggle -> save { copy(dopplerEnabled = event.enabled) }
                is BlackHoleSettingsEvent.OnOpacityFalloffToggle -> save { copy(diskOpacityFalloff = event.enabled) }
                is BlackHoleSettingsEvent.OnLayerToggle -> save { event.layer.with(this, event.enabled) }
                is BlackHoleSettingsEvent.OnIdleFpsChange -> save { copy(idleFps = event.fps) }
                is BlackHoleSettingsEvent.OnSliderChangeFinished -> {
                    // The drag has been folded into the state already; only now does the value reach the store.
                    val value = event.slider.value(state.value.settings)
                    save { event.slider.with(this, value) }
                }
                BlackHoleSettingsEvent.OnShuffleStarsClick -> save { copy(starSeed = newStarSeed(starSeed)) }
                BlackHoleSettingsEvent.OnResetClick -> save { BlackHoleSettings() }
                BlackHoleSettingsEvent.OnDebugClick ->
                    if (state.value.isDebugAvailable) navigator.navigate(BlackHoleDebugDestination)
                // Every stored change passes here; the tables follow only when their key moves.
                is BlackHoleSettingsEvent.OnSettingsChanged -> loadTables(state.value.wantedTablesKey)
                else -> Unit
            }
        }.launchIn(viewModelScope)

        settingsSource.settings
            .onEach { settings -> onEvent(BlackHoleSettingsEvent.OnSettingsChanged(settings)) }
            .launchIn(viewModelScope)
    }

    private inline fun save(crossinline transform: BlackHoleSettings.() -> BlackHoleSettings) =
        settingsSource.update { it.transform() }

    /** Only the newest key is worth the CPU: a load for an older one is cancelled. */
    private fun loadTables(key: LensTableKey) {
        if (key == requestedKey) return
        requestedKey = key
        tablesJob?.cancel()
        tablesJob = viewModelScope.launch {
            try {
                onEvent(BlackHoleSettingsEvent.OnTablesLoaded(key, tablesSource.get(key)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Lens tables %s failed", key)
                requestedKey = null
                onEvent(BlackHoleSettingsEvent.OnTablesLoadFailed(key, e.message ?: e.toString()))
            }
        }
    }

    /** Any other positive seed: the sky changes on every press. */
    private fun newStarSeed(current: Int): Int =
        generateSequence { Random.nextInt(1, Int.MAX_VALUE) }.first { it != current }

    private companion object {
        const val TAG = "BlackHole"
    }
}
