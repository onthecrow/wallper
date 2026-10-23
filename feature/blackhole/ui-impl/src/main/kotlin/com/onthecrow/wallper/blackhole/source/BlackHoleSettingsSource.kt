package com.onthecrow.wallper.blackhole.source

import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettings
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettingsStore
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** The user settings of the black hole; [BlackHoleSettingsStore] in the app, a fake in tests. */
internal interface BlackHoleSettingsSource {
    val settings: StateFlow<BlackHoleSettings>
    fun update(transform: (BlackHoleSettings) -> BlackHoleSettings)
}

internal class StoreBlackHoleSettingsSource @Inject constructor(
    private val store: BlackHoleSettingsStore,
) : BlackHoleSettingsSource {
    override val settings: StateFlow<BlackHoleSettings> get() = store.settings
    override fun update(transform: (BlackHoleSettings) -> BlackHoleSettings) = store.update(transform)
}
