package com.onthecrow.wallper.blackhole.engine

import android.content.Context
import com.onthecrow.wallper.blackhole.data.BlackHoleSettingsStore
import com.onthecrow.wallper.blackhole.data.LensTablesRepository
import com.onthecrow.wallper.blackhole.physics.BlackHoleParams
import com.onthecrow.wallper.blackhole.physics.BlackHoleScene
import com.onthecrow.wallper.blackhole.physics.LensTables
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.physics.lensTableKey
import com.onthecrow.wallper.blackhole.renderer.BlackHoleGlRenderer
import com.onthecrow.wallper.blackhole.renderer.BlackHoleRenderer
import com.onthecrow.wallper.core.engine.BuiltInScene
import com.onthecrow.wallper.core.gl.GLWallpaperRenderer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

/**
 * The black hole for a series' preview: the wallpaper's own [BlackHoleRenderer], with the user's settings,
 * at the cheap quality of the system picker's preview, drawn as a [BlackHoleScene] is at the moment it is
 * given. Its lens tables load in the background and reach the renderer at its next frame, on its GL thread;
 * so do they again after a lost context. Until then its frames have no picture.
 *
 * No gyroscope, no pages and no frame pacing of its own: the preview moves it.
 */
internal class BlackHolePreviewScene(
    context: Context,
    settingsStore: BlackHoleSettingsStore,
    private val tablesRepository: LensTablesRepository,
    private val onChanged: () -> Unit,
) : BuiltInScene {

    private val params = settingsStore.settings.value.applyTo(BlackHoleParams())
    private val scene = BlackHoleScene(params)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, error -> Timber.e(error, "The black hole's preview can't load its tables") },
    )

    /** Tables loaded and not yet given to the renderer, which takes them on its GL thread. */
    private val loadedTables = AtomicReference<LensTables?>(null)

    private val blackHole = BlackHoleRenderer(context.assets).apply {
        // A slide comes whole, not out of black.
        fadeIn = false
        setQuality(QUALITY)
        setParams(params)
        onTablesLost = ::loadTables
    }
    private val glRenderer = BlackHoleGlRenderer(blackHole)

    override val renderer: GLWallpaperRenderer = object : GLWallpaperRenderer() {
        override val hasPicture: Boolean get() = glRenderer.hasPicture

        override fun onSurfaceCreated() = glRenderer.onSurfaceCreated()

        override fun onSurfaceChanged(width: Int, height: Int) = glRenderer.onSurfaceChanged(width, height)

        override fun onDrawFrame(): Boolean {
            loadedTables.getAndSet(null)?.let(blackHole::setTables)
            return glRenderer.onDrawFrame()
        }

        override fun onContextLost() = glRenderer.onContextLost()

        override fun dispose() = glRenderer.dispose()
    }

    init {
        loadTables()
    }

    override fun showAt(timeNanos: Long) = glRenderer.submit(scene.frame(timeNanos))

    override fun release() = scope.cancel()

    private fun loadTables() {
        scope.launch {
            loadedTables.set(tablesRepository.get(lensTableKey(params, QUALITY)))
            onChanged()
        }
    }

    private companion object {
        /** As in the system picker's preview: the phone frame is a small window. */
        val QUALITY = QualityLevel.LOW
    }
}
