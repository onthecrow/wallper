package com.onthecrow.wallper.core.engine.impl.engine

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.EngineHost
import com.onthecrow.wallper.core.engine.SlideStart
import com.onthecrow.wallper.core.engine.WallpaperSettingsSource
import com.onthecrow.wallper.core.engine.WallpaperEngine
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.engine.impl.BuiltInWallpapers
import com.onthecrow.wallper.core.gl.OwnGl
import com.onthecrow.wallper.core.gl.SharedGl
import com.onthecrow.wallper.core.model.ClipRange
import com.onthecrow.wallper.core.model.CropRect
import com.onthecrow.wallper.core.model.MediaInfo
import com.onthecrow.wallper.core.model.MediaType
import com.onthecrow.wallper.core.model.SeriesSwitch
import com.onthecrow.wallper.core.player.WallperPlayerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.time.TimeSource

internal class WallpaperEngineFactory @Inject constructor(
    private val playerFactory: WallperPlayerFactory,
    private val builtIns: BuiltInWallpapers,
    private val wallpaperSettings: WallpaperSettingsSource,
    private val powerSaving: PowerSaving,
    private val screenOffs: ScreenOffs,
) {
    /**
     * @param wallpaper the wallpaper to show, or null for the placeholder.
     * @param isPreview the system picker's preview: built-ins render cheaper there, and a series cuts
     * whatever [seriesSwitch] says: the wallpaper itself lives beside the preview, with decoders of its own.
     * @param seriesSwitch how a series changes its slides.
     */
    fun create(
        baseContext: Context,
        surfaceHolder: () -> SurfaceHolder,
        wallpaper: WallpaperSpec?,
        isPreview: Boolean,
        host: EngineHost,
        seriesSwitch: SeriesSwitch = SeriesSwitch.CUT,
    ): WallpaperEngine = when (wallpaper) {
        null -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        is WallpaperSpec.BuiltIn -> builtIns[wallpaper.wallpaper]
            ?.createEngine(baseContext, surfaceHolder, isPreview, host)
            ?: PlaceholderWallpaperEngine(baseContext, surfaceHolder)
        is WallpaperSpec.ProcessedVideo -> optimisedVideo(surfaceHolder, wallpaper, host)
        is WallpaperSpec.Media -> when (wallpaper.type) {
            MediaType.VIDEO -> originalVideo(surfaceHolder, wallpaper, host)
            MediaType.IMAGE -> PictureWallpaperEngine(surfaceHolder = surfaceHolder, wallpaper = wallpaper, host = host)
        }
        is WallpaperSpec.Series -> {
            // A series never holds a series; a single slide needs none.
            val slides = wallpaper.slides.filterNot { it is WallpaperSpec.Series }
            when (slides.size) {
                0 -> PlaceholderWallpaperEngine(baseContext, surfaceHolder)
                1 -> create(baseContext, surfaceHolder, slides.single(), isPreview, host)
                else -> {
                    val scope = CoroutineScope(Dispatchers.Main.immediate)
                    SeriesWallpaperEngine(
                        slideCount = slides.size,
                        slideDuration = wallpaper.slideDuration,
                        screenOffs = screenOffs.events,
                        isScreenOn = { screenOffs.isScreenOn },
                        scope = scope,
                        timeSource = TimeSource.Monotonic,
                        host = host,
                        newStage = { hostOf ->
                            val switch = if (isPreview) SeriesSwitch.CUT else seriesSwitch
                            stage(switch, baseContext, surfaceHolder, slides, isPreview, host, hostOf, scope)
                        },
                    )
                }
            }
        }
    }

    /**
     * How a series' slides get onto the screen.
     *
     * @param host the series' own: it hears when the series as a whole can't go on.
     * @param hostOf the host of each slide: a slide that gives up is skipped.
     */
    private fun stage(
        switch: SeriesSwitch,
        baseContext: Context,
        surfaceHolder: () -> SurfaceHolder,
        slides: List<WallpaperSpec>,
        isPreview: Boolean,
        host: EngineHost,
        hostOf: (slide: Int) -> EngineHost,
        scope: CoroutineScope,
    ): SlideStage = when (switch) {
        SeriesSwitch.CUT -> CutStage(slides, hostOf) { slide, slideHost ->
            create(baseContext, surfaceHolder, slide, isPreview, slideHost)
        }
        // Every slide's renderer on one GL thread that keeps the window: a change is its next frame.
        SeriesSwitch.SHARED_GL -> {
            val gl = SharedGl(surfaceHolder(), glesVersionOf(slides), host::showPlaceholder)
            WarmStage(
                slides = slides,
                hostOf = hostOf,
                scope = scope,
                handsOverWindow = false,
                window = SharedGlWindow(gl),
            ) { slide, slideHost, onReady ->
                // An optimised video goes through GL here too: nothing but GL draws on this window.
                val drawn = if (slide is WallpaperSpec.ProcessedVideo) slide.asWholeFrame() else slide
                slide(baseContext, surfaceHolder, drawn, isPreview, slideHost, SlideStart(gl, onReady))
            }
        }
        // Every slide on a GL thread of its own, which starts without the window: the slide on screen has it.
        SeriesSwitch.WARM_ENGINES -> WarmStage(
            slides = slides,
            hostOf = hostOf,
            scope = scope,
            handsOverWindow = true,
        ) { slide, slideHost, onReady ->
            if (slide is WallpaperSpec.ProcessedVideo) {
                // Straight onto the window, as outside a series: no GL pass for a video that needs no crop.
                optimisedVideo(surfaceHolder, slide, slideHost, onReady)
            } else {
                val start = SlideStart(OwnGl(surfaceHolder(), offWindow = true), onReady)
                slide(baseContext, surfaceHolder, slide, isPreview, slideHost, start)
            }
        }
    }

    /** A slide's engine made off screen on [start]'s GL host; null if it can't be made that way. */
    private fun slide(
        baseContext: Context,
        surfaceHolder: () -> SurfaceHolder,
        slide: WallpaperSpec,
        isPreview: Boolean,
        host: EngineHost,
        start: SlideStart,
    ): WallpaperEngine? = when (slide) {
        is WallpaperSpec.Media -> when (slide.type) {
            MediaType.VIDEO -> originalVideo(surfaceHolder, slide, host, start)
            MediaType.IMAGE -> PictureSlideEngine(surfaceHolder, slide, host, start)
        }
        is WallpaperSpec.BuiltIn -> builtIns[slide.wallpaper]
            ?.takeIf { it.isSupported() }
            ?.createSlide(baseContext, surfaceHolder, isPreview, host, start)
        // An optimised video is the stage's to place; a series holds no series.
        is WallpaperSpec.ProcessedVideo, is WallpaperSpec.Series -> null
    }

    /**
     * The highest OpenGL ES version a slide's renderer asks for: 3 only with a built-in that needs it, so
     * that a series of videos and pictures keeps the context they have outside one.
     */
    private fun glesVersionOf(slides: List<WallpaperSpec>): Int = slides
        .filterIsInstance<WallpaperSpec.BuiltIn>()
        .mapNotNull { slide -> builtIns[slide.wallpaper]?.takeIf { it.isSupported() }?.glesVersion }
        .maxOrNull()
        ?: DEFAULT_GLES_VERSION

    /**
     * An optimised video as the GL engine takes one: its whole frame, a frame of one by one cropped to all
     * of it, over the whole screen, never panned, played from end to end. Without its tint: the export put
     * that into the frames already.
     */
    private fun WallpaperSpec.ProcessedVideo.asWholeFrame() = WallpaperSpec.Media(
        type = MediaType.VIDEO,
        sourcePath = path,
        thumbnailPath = thumbnailPath,
        crop = CropRect(left = 0, top = 0, right = 1, bottom = 1),
        clip = ClipRange(startMs = 0, endMs = 0),
        mediaInfo = MediaInfo(width = 1, height = 1, rotation = 0, durationMs = 0),
    )

    private fun originalVideo(
        surfaceHolder: () -> SurfaceHolder,
        wallpaper: WallpaperSpec.Media,
        host: EngineHost,
        slide: SlideStart? = null,
    ) = GLWallpaperEngine(
        surfaceHolder = surfaceHolder,
        wallpaper = wallpaper,
        playerFactory = playerFactory,
        settings = wallpaperSettings.settings,
        savingPower = powerSaving.isSaving,
        screenOffs = screenOffs.events,
        host = host,
        slide = slide,
    )

    private fun optimisedVideo(
        surfaceHolder: () -> SurfaceHolder,
        wallpaper: WallpaperSpec.ProcessedVideo,
        host: EngineHost,
        onReady: (() -> Unit)? = null,
    ) = VideoWallpaperEngine(
        surfaceHolder = surfaceHolder,
        processedPath = wallpaper.path,
        playerFactory = playerFactory,
        settings = wallpaperSettings.settings,
        savingPower = powerSaving.isSaving,
        screenOffs = screenOffs.events,
        host = host,
        onReady = onReady,
    )

    private companion object {
        /** What the video and picture renderers are written for. */
        const val DEFAULT_GLES_VERSION = 2
    }
}
