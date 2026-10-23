package com.onthecrow.wallper.core.engine.impl

import com.onthecrow.wallper.core.engine.BuiltInWallpaperProvider
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import javax.inject.Inject

/** The built-ins the features provide; core knows none of them by name. */
internal class BuiltInWallpapers @Inject constructor(
    providers: Set<@JvmSuppressWildcards BuiltInWallpaperProvider>,
) {
    private val byKey = providers.associateBy { it.wallpaper }

    operator fun get(wallpaper: BuiltInWallpaper): BuiltInWallpaperProvider? = byKey[wallpaper]
}
