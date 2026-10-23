package com.onthecrow.wallper.editor

import com.onthecrow.wallper.core.navigation.Destination
import kotlinx.serialization.Serializable

/**
 * Editor for a freshly picked media [uri].
 *
 * TODO Edit mode: open an existing wallpaper by id.
 */
@Serializable
data class EditorDestination(val uri: String) : Destination
