package com.onthecrow.wallper.editor.domain.model

sealed interface ExportStatus {
    /**
     * An export under way: [progress] in percent, as Transformer gives it, about how long it still takes
     * ([remainingMs], null while it is too early to tell), and what it makes ([output]) once that is known.
     */
    data class InProgress(
        val progress: Int = 0,
        val remainingMs: Long? = null,
        val output: ExportOutput? = null,
    ) : ExportStatus

    data class Error(val error: Throwable) : ExportStatus
    data object Success : ExportStatus
}
