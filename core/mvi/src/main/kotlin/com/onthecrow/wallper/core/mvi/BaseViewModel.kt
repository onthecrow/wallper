package com.onthecrow.wallper.core.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

/**
 * MVI base shared with NomadRates and OnthecrowVPN: every event goes through [reducer] via
 * `runningFold`, side effects collect [eventFlow] in the subclass.
 *
 * Two differences from those projects:
 * - the event buffer is unbounded instead of 64 + DROP_OLDEST, so bursts from the editor
 *   (trim bar, crop gestures) are never dropped;
 * - the initial state is a constructor argument instead of an abstract `getInitialState()`,
 *   because a state built from injected arguments is not available yet while the base class
 *   constructor runs.
 */
abstract class BaseViewModel<E : Event, S : State, R : Reducer<S, E>>(
    initialState: S,
    private val reducer: R,
) : ViewModel() {

    private val _event: MutableSharedFlow<E> = MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)
    val eventFlow = _event.asSharedFlow()

    val state: StateFlow<S> = _event.runningFold(initialState, reducer::reduce)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialState)

    fun onEvent(event: E) {
        _event.tryEmit(event)
    }
}
