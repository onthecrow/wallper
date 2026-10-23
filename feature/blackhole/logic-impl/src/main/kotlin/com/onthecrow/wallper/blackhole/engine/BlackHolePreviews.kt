package com.onthecrow.wallper.blackhole.engine

import android.content.Context
import com.onthecrow.wallper.blackhole.data.BlackHoleSettingsStore
import com.onthecrow.wallper.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.blackhole.physics.FrameParams
import com.onthecrow.wallper.blackhole.physics.InputParams
import com.onthecrow.wallper.blackhole.renderer.BlackHolePreviewRenderer
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import java.util.zip.CRC32
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stills of the black hole for the wallpaper list, rendered with the wallpaper's own renderer, again
 * whenever the settings change the picture. Keyed by what the picture shows, so a launch with the same
 * settings reuses the file.
 */
@Singleton
internal class BlackHolePreviews @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val renderer: BlackHolePreviewRenderer,
    private val settingsStore: BlackHoleSettingsStore,
) {

    private val directory by lazy { File(context.createDeviceProtectedStorageContext().filesDir, "previews") }

    /** Changes with every install of the app: new shaders or defaults render the pictures once more. */
    private val installedAt by lazy { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }

    /** One render at a time: two screens asking for the same picture still write one file. */
    private val renderLock = Mutex()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun previews(): Flow<String> = settingsStore.settings
        // A still frame doesn't show the motion or the frame rate.
        .map { it.applyTo(BlackHoleParams()).copy(input = InputParams(), frames = FrameParams()) }
        .distinctUntilChanged()
        .debounce(SETTINGS_DEBOUNCE_MS)
        .mapLatest(::preview)
        .filterNotNull()

    private suspend fun preview(params: BlackHoleParams): String? = try {
        renderLock.withLock {
            val prefix = BuiltInWallpaper.BLACK_HOLE.key
            // toString() of the params is the same in every process; hashCode() of their enums is not.
            val key = CRC32().apply { update("$installedAt $params".toByteArray()) }.value
            val file = File(directory, "${prefix}_$key.png")
            val metrics = context.resources.displayMetrics
            val height = (PREVIEW_WIDTH * metrics.heightPixels.toFloat() / metrics.widthPixels.coerceAtLeast(1)).toInt()
            if (file.exists() || renderer.render(params, file, PREVIEW_WIDTH, height)) {
                // Older pictures go only now: the list keeps showing one until the new one is ready.
                directory.listFiles { old -> old.name.startsWith(prefix) && old != file }?.forEach(File::delete)
                file.path
            } else {
                null
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e, "No preview for the black hole")
        null
    }

    private companion object {
        const val PREVIEW_WIDTH = 540
        const val SETTINGS_DEBOUNCE_MS = 300L
    }
}
