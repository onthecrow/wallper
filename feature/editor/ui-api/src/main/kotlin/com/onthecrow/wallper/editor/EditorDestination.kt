package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.navigation.Destination
import kotlinx.serialization.Serializable

/** The editor: crops and trims picked media into a new wallpaper, or a saved one again. */
sealed interface EditorDestination : Destination {

    /** Freshly picked media [uri]. */
    @Serializable
    data class New(val uri: String) : EditorDestination

    /**
     * Saved wallpaper [wallpaperId]. Only one that keeps its original can be edited. With [optimise] the
     * editor starts by switching to optimised, as if the user did: the first time it explains it first.
     */
    @Serializable
    data class Edit(val wallpaperId: Int, val optimise: Boolean = false) : EditorDestination
}
