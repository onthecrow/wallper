package com.onthecrow.wallper.editor.domain

/** Whether the user has seen what additional processing does: the editor explains it before the first such save. */
interface ProcessingInfoMemory {
    suspend fun isSeen(): Boolean

    fun markSeen()
}
