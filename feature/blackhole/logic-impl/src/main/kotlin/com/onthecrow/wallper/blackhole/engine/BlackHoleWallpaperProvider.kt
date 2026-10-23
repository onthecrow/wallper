package com.onthecrow.wallper.blackhole.engine

import android.app.ActivityManager
import android.app.WallpaperColors
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import com.onthecrow.wallper.blackhole.data.AutoQualityMemory
import com.onthecrow.wallper.blackhole.data.BlackHoleSettingsStore
import com.onthecrow.wallper.blackhole.data.LensTablesRepository
import com.onthecrow.wallper.core.engine.BuiltInWallpaperProvider
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.model.BuiltInWallpaper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The black hole for the service, the list and the system picker. */
internal class BlackHoleWallpaperProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsStore: BlackHoleSettingsStore,
    private val tablesRepository: LensTablesRepository,
    private val autoQuality: AutoQualityMemory,
    private val listPreviews: BlackHolePreviews,
) : BuiltInWallpaperProvider {

    override val wallpaper = BuiltInWallpaper.BLACK_HOLE

    /** The renderer needs OpenGL ES 3.0. */
    override fun isSupported(): Boolean {
        val version = context.getSystemService<ActivityManager>()?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        return version >= GLES_3_0
    }

    override fun createEngine(
        context: Context,
        surfaceHolder: () -> SurfaceHolder,
        isPreview: Boolean,
        host: EngineHost,
    ): WallpaperEngine = BlackHoleWallpaperEngine(
        context, surfaceHolder, isPreview, host, settingsStore, tablesRepository, autoQuality,
    )

    /** Almost black with a warm amber accent, dark theme friendly (BLACKHOLE_TZ.md §11.4). */
    @RequiresApi(Build.VERSION_CODES.O_MR1)
    override fun colors(): WallpaperColors {
        val primary = Color.valueOf(0.03f, 0.025f, 0.02f)
        val secondary = Color.valueOf(0.85f, 0.55f, 0.2f)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WallpaperColors(primary, secondary, null, WallpaperColors.HINT_SUPPORTS_DARK_THEME)
        } else {
            WallpaperColors(primary, secondary, null)
        }
    }

    override fun previews(): Flow<String> = listPreviews.previews()

    private companion object {
        const val GLES_3_0 = 0x30000
    }
}
