package com.onthecrow.wallper.core.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

abstract class BaseViewModel<State : BaseState<Action>, Action, Event>(
    initialState: State
) : ViewModel() {

    private val mutableUiState: MutableStateFlow<State> = MutableStateFlow(initialState)
    val uiState: StateFlow<State> get() = mutableUiState.asStateFlow()

    protected fun performAction(action: Action) {
        updateUiState { setActions(actions + action) }
    }

    fun completedAction(action: Action) {
        updateUiState { setActions(actions - action) }
    }

    protected fun updateUiState(transform: State.() -> State) {
        mutableUiState.update { state -> state.transform() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun State.setActions(newActions: List<Action>): State =
        updateActions(newActions) as State

    abstract fun sendEvent(uiEvent: Event)
}
