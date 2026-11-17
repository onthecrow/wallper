package com.onthecrow.wallper.core.engine

/** The user's wallpapers as the engines take them, by id: what a preview inside the app plays. Implemented by the feature that owns wallpapers. */
interface WallpaperSpecSource {
    /** The wallpapers [ids], in their order, for a series' slides: those that are gone, and groups, are left out. */
    suspend fun slides(ids: List<Int>): List<WallpaperSpec>
}
