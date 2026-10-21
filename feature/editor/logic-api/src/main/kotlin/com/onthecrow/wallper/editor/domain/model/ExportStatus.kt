package com.onthecrow.wallper.editor.domain.model

sealed interface ExportStatus {
    data class InProgress(val progress: Int = 0) : ExportStatus
    data class Error(val error: Throwable) : ExportStatus
    data object Success : ExportStatus
}
