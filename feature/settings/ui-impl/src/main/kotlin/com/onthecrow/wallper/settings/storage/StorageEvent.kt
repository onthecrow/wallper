package com.onthecrow.wallper.settings.storage

import com.onthecrow.wallper.core.mvi.Event

internal sealed interface StorageEvent : Event {
    // User actions
    data object OnBackClick : StorageEvent
    data class OnEditClick(val id: Int) : StorageEvent
    data class OnDeleteClick(val id: Int) : StorageEvent
    data class OnDeleteConfirm(val id: Int) : StorageEvent
    data object OnDeleteDismiss : StorageEvent

    // Internal events
    data class OnItemsChanged(val items: List<StorageItem>) : StorageEvent
}
