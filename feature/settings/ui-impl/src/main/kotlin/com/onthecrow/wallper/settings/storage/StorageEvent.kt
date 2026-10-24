package com.onthecrow.wallper.settings.storage

import com.onthecrow.wallper.core.mvi.Event

internal sealed interface StorageEvent : Event {
    // User actions
    data object OnBackClick : StorageEvent
    data class OnOptimiseClick(val id: Int) : StorageEvent

    // Internal events
    data class OnItemsChanged(val items: List<StorageItem>) : StorageEvent
}
