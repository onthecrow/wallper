package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.blackhole.BlackHoleSettingsDestination
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import com.onthecrow.wallper.core.navigation.Destination

/** Where a built-in's pencil goes: its settings, from the list's card and from a series' member row alike. */
internal fun BuiltInWallpaper.settingsDestination(): Destination = when (this) {
    BuiltInWallpaper.BLACK_HOLE -> BlackHoleSettingsDestination
}
