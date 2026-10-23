package com.onthecrow.wallper.editor.cropper

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.onthecrow.wallper.editor.preview.EditorPlayer

/** Shows what [player] plays. The player belongs to the caller and outlives this view. */
@Composable
internal fun VideoSurface(
    player: EditorPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = ::TextureView,
        // Runs right after factory too.
        update = { view ->
            player.setVideoTextureView(view)
        },
        onReset = { view ->
            player.setVideoTextureView(null)
            view.surfaceTextureListener = null
        },
        onRelease = { view ->
            player.setVideoTextureView(null)
            view.surfaceTextureListener = null
        },
    )
}
