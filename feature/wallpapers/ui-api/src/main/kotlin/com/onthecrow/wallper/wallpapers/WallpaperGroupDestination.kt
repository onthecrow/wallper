package com.onthecrow.wallper.wallpapers

import com.onthecrow.wallper.core.navigation.Destination
import kotlinx.serialization.Serializable

/** A group of wallpapers shown in turn (a slideshow): a new one without [groupId], otherwise that one. */
@Serializable
data class WallpaperGroupDestination(val groupId: Int? = null) : Destination
