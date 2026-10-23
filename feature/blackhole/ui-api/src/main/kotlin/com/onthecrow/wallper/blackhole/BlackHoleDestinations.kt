package com.onthecrow.wallper.blackhole

import com.onthecrow.wallper.core.navigation.Destination
import kotlinx.serialization.Serializable

/** Settings of the built-in black hole wallpaper. */
@Serializable
data object BlackHoleSettingsDestination : Destination

/** Debug builds only: the black hole renderer with live parameters, FPS and the reference mode. */
@Serializable
data object BlackHoleDebugDestination : Destination
