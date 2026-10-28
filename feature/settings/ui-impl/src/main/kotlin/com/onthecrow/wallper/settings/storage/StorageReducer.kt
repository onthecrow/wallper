package com.onthecrow.wallper.settings.storage

import com.onthecrow.wallper.core.mvi.Reducer
import javax.inject.Inject

internal class StorageReducer @Inject constructor() : Reducer<StorageState, StorageEvent> {
    override suspend fun reduce(state: StorageState, event: StorageEvent): StorageState =
        when (event) {
            is StorageEvent.OnItemsChanged -> state.copy(items = event.items.sortedByDescending { it.bytes })
            is StorageEvent.OnDeleteClick -> state.copy(deleteCandidate = event.id)
            is StorageEvent.OnDeleteConfirm, StorageEvent.OnDeleteDismiss -> state.copy(deleteCandidate = null)
            else -> state
        }
}
