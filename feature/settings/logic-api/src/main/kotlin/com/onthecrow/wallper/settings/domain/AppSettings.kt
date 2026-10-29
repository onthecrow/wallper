package com.onthecrow.wallper.settings.domain

import com.onthecrow.wallper.core.model.VideoPlayback
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What the user may change about the app as a whole; the black hole has settings of its own.
 * Stored as JSON ([encode], [decode]) with only what differs from the defaults: a setting the user never
 * changed follows the defaults of the next version. A field's name is its stored key: never rename one.
 */
@Serializable
data class AppSettings(
    val videoPlayback: VideoPlayback = VideoPlayback.LOOP,
    /** Pause video wallpapers in Battery Saver and when the phone is hot. */
    val pauseVideoToSaveBattery: Boolean = true,
    /** Move every wallpaper with the launcher's pages: original videos pan, the black hole turns. */
    val scrollVideoWithPages: Boolean = true,
    /** Make a wallpaper the active one as soon as it is added. */
    val activateNewWallpapers: Boolean = true,
    /** List the built-in wallpapers; the active one stays listed anyway. */
    val showBuiltInWallpapers: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Material You: the phone's system colours, taken from its wallpaper or picked in its settings (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Let Firebase collect usage statistics (Analytics) and crash reports (Crashlytics); only release builds have it. */
    val shareUsageAndCrashData: Boolean = true,
) {
    /** Only what differs from the defaults. */
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true // a setting a newer version stored and this one doesn't know
            coerceInputValues = true // an enum value this version doesn't know becomes the default
        }

        /** Malformed text gives the defaults. */
        fun decode(text: String): AppSettings = try {
            json.decodeFromString(serializer(), text)
        } catch (_: SerializationException) {
            AppSettings()
        } catch (_: IllegalArgumentException) {
            AppSettings()
        }
    }
}

/** Stored by name: never rename one. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
