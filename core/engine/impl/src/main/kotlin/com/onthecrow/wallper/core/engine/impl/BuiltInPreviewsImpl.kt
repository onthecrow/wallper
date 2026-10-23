package com.onthecrow.wallper.core.engine.impl

import android.content.Context
import com.onthecrow.wallper.core.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.core.blackhole.renderer.BlackHolePreviewRenderer
import com.onthecrow.wallper.core.blackhole.renderer.BlackHoleSettingsStore
import com.onthecrow.wallper.core.engine.BuiltInPreviews
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Renders built-in previews with the wallpaper's own renderer; one PNG per wallpaper and settings. */
internal class BuiltInPreviewsImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val blackHole: BlackHolePreviewRenderer,
    private val blackHoleSettings: BlackHoleSettingsStore,
) : BuiltInPreviews {

    private val directory by lazy { File(context.createDeviceProtectedStorageContext().filesDir, "previews") }

    override suspend fun preview(wallpaper: BuiltInWallpaper): String? = try {
        when (wallpaper) {
            BuiltInWallpaper.BLACK_HOLE -> blackHolePreview()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "No preview for $wallpaper")
        null
    }

    private suspend fun blackHolePreview(): String? {
        val settings = blackHoleSettings.settings.value
        // The picture depends on the settings: a new file whenever they change.
        val name = "${BuiltInWallpaper.BLACK_HOLE.key}_v${PREVIEW_VERSION}_${settings.hashCode().toUInt()}.png"
        val file = File(directory, name)
        if (file.exists()) return file.path
        directory.listFiles { old -> old.name.startsWith(BuiltInWallpaper.BLACK_HOLE.key) }?.forEach(File::delete)
        val metrics = context.resources.displayMetrics
        val width = PREVIEW_WIDTH
        val height = (PREVIEW_WIDTH * metrics.heightPixels.toFloat() / metrics.widthPixels.coerceAtLeast(1)).toInt()
        return file.path.takeIf { blackHole.render(settings.applyTo(BlackHoleParams()), file, width, height) }
    }

    private companion object {
        const val PREVIEW_WIDTH = 540
        const val PREVIEW_VERSION = 1
    }
}
