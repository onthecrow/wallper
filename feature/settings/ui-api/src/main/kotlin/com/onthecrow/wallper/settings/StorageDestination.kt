package com.onthecrow.wallper.settings

import com.onthecrow.wallper.core.navigation.Destination
import kotlinx.serialization.Serializable

/** What the user's wallpapers take of the device's storage, opened from the settings. */
@Serializable
data object StorageDestination : Destination
