package com.onthecrow.wallper.settings.domain

/**
 * Writes what helps to understand a problem to a text file for the user to share: the app's version, the
 * device and the app's recent log. Replaces the previous report. Returns the file's path.
 */
interface CreateReportUseCase {
    suspend operator fun invoke(): String
}
