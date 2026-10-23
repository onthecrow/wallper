package com.onthecrow.wallper.blackhole.model

import com.onthecrow.wallper.core.blackhole.physics.BlackHoleSettings

/** A layer of the picture the user can switch off; stored like any setting. */
internal enum class PictureLayer {
    DISK {
        override fun isOn(settings: BlackHoleSettings) = settings.diskEnabled
        override fun with(settings: BlackHoleSettings, on: Boolean) = settings.copy(diskEnabled = on)
    },
    STARS {
        override fun isOn(settings: BlackHoleSettings) = settings.starsEnabled
        override fun with(settings: BlackHoleSettings, on: Boolean) = settings.copy(starsEnabled = on)
    },
    HAZE {
        override fun isOn(settings: BlackHoleSettings) = settings.hazeEnabled
        override fun with(settings: BlackHoleSettings, on: Boolean) = settings.copy(hazeEnabled = on)
    },
    BLOOM {
        override fun isOn(settings: BlackHoleSettings) = settings.bloomEnabled
        override fun with(settings: BlackHoleSettings, on: Boolean) = settings.copy(bloomEnabled = on)
    };

    abstract fun isOn(settings: BlackHoleSettings): Boolean
    abstract fun with(settings: BlackHoleSettings, on: Boolean): BlackHoleSettings
}
