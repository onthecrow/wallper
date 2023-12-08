package com.onthecrow.wallper.domain

import com.onthecrow.wallper.data.SettingsRepository
import com.onthecrow.wallper.data.model.ScreenResolution
import javax.inject.Inject

class GetScreenResolutionUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): ScreenResolution {
        return settingsRepository.getScreenResolution()
    }
}
