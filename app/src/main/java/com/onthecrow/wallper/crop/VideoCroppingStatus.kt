package com.onthecrow.wallper.crop

sealed class VideoCroppingStatus {
    data class InProgress(val progress: Int = 0, val remainingTime: Int = -1, val elapsedTime: Int = -1) : VideoCroppingStatus()
    data class Error(val error: Throwable = IllegalStateException("Unknown")) : VideoCroppingStatus()
    data object Success : VideoCroppingStatus()
    data object Undefinite : VideoCroppingStatus()
}