package com.onthecrow.wallper.core.model

/**
 * Wallpapers that ship with the app instead of being made from the user's media.
 * [key] is stored in the database: never rename it.
 */
enum class BuiltInWallpaper(val key: String) {
    BLACK_HOLE("black_hole");

    companion object {
        fun fromKey(key: String): BuiltInWallpaper? = entries.find { it.key == key }
    }
}
