package com.onthecrow.wallper.blackhole.source

import com.onthecrow.wallper.blackhole.physics.BlackHoleSettings
import kotlinx.coroutines.flow.StateFlow

/** The user settings of the black hole, stored in device-protected storage; one instance per process. */
interface BlackHoleSettingsSource {
    val settings: StateFlow<BlackHoleSettings>
    fun update(transform: (BlackHoleSettings) -> BlackHoleSettings)
}
