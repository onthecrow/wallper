package com.onthecrow.wallper.blackhole

import androidx.lifecycle.viewModelScope
import com.onthecrow.wallper.blackhole.source.BlackHoleSettingsSource
import com.onthecrow.wallper.blackhole.source.LensTablesSource
import com.onthecrow.wallper.core.blackhole.physics.LensTableKey
import com.onthecrow.wallper.core.mvi.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Starts from the stored settings and never writes them back. Loads the lens tables for the current
 * distance and quality; the GL side of the screen draws whatever the state holds.
 */
@HiltViewModel
internal class BlackHoleDebugViewModel @Inject constructor(
    reducer: BlackHoleDebugReducer,
    settingsSource: BlackHoleSettingsSource,
    private val tablesSource: LensTablesSource,
) : BaseViewModel<BlackHoleDebugEvent, BlackHoleDebugState, BlackHoleDebugReducer>(
    BlackHoleDebugState.initial(settingsSource.settings.value),
    reducer,
) {

    /** Touched only from [eventFlow] and [init], on the main thread. */
    private var requestedKey: LensTableKey? = null
    private var tablesJob: Job? = null

    init {
        eventFlow.onEach { event ->
            when (event) {
                // The reducer has folded the event in already, so the state names the tables it wants.
                is BlackHoleDebugEvent.OnQualityChange,
                BlackHoleDebugEvent.OnDistanceChangeFinished -> loadTables(state.value.wantedTablesKey)
                else -> Unit
            }
        }.launchIn(viewModelScope)

        loadTables(state.value.wantedTablesKey)
    }

    /** Only the newest key is worth the CPU: a load for an older one is cancelled. */
    private fun loadTables(key: LensTableKey) {
        if (key == requestedKey) return
        requestedKey = key
        tablesJob?.cancel()
        tablesJob = viewModelScope.launch {
            try {
                onEvent(BlackHoleDebugEvent.OnTablesLoaded(key, tablesSource.get(key)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Lens tables %s failed", key)
                requestedKey = null
                onEvent(BlackHoleDebugEvent.OnTablesLoadFailed(key, e.message ?: e.toString()))
            }
        }
    }

    private companion object {
        const val TAG = "BlackHole"
    }
}
