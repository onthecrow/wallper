package com.onthecrow.wallper.core.engine

import com.onthecrow.wallper.core.gl.GlHost

/**
 * For an engine made as a slide of a series that prepares its slides ahead: it starts off screen, hidden,
 * while the slide before it still shows, and takes the window at [WallpaperEngine.show].
 *
 * @param gl where its renderer runs: a GL thread of its own that starts without the window, or the one the
 * series' slides share.
 * @param onReady to call once, from any thread, when its picture is drawn and it can be shown at once.
 */
class SlideStart(val gl: GlHost, val onReady: () -> Unit)
