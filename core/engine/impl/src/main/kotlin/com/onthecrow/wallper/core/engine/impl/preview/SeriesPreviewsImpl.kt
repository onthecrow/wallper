package com.onthecrow.wallper.core.engine.impl.preview

import android.content.Context
import android.view.SurfaceHolder
import com.onthecrow.wallper.core.engine.SeriesPreview
import com.onthecrow.wallper.core.engine.SeriesPreviews
import com.onthecrow.wallper.core.engine.WallpaperSpec
import com.onthecrow.wallper.core.engine.impl.BuiltInWallpapers
import com.onthecrow.wallper.core.model.SlideTransition
import javax.inject.Inject

internal class SeriesPreviewsImpl @Inject constructor(
    private val builtIns: BuiltInWallpapers,
) : SeriesPreviews {
    override fun create(
        context: Context,
        surfaceHolder: SurfaceHolder,
        slides: List<WallpaperSpec>,
        transition: SlideTransition,
        holdMs: Long,
    ): SeriesPreview = SeriesPreviewImpl(context, surfaceHolder, slides, transition, holdMs, builtIns)
}
