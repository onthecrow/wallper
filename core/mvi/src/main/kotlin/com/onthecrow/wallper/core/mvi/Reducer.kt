package com.onthecrow.wallper.core.mvi

interface Reducer<S : State, E : Event> {
    suspend fun reduce(state: S, event: E): S
}
