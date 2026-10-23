package com.onthecrow.wallper.editor.cropper

import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.onthecrow.wallper.editor.preview.EditorPlayer

/**
 * Shows what [player] plays. The player belongs to the caller and outlives this view. Each new ExoPlayer
 * inside it gets a new TextureView: a decoder that stopped may still hold the old one's surface.
 */
@Composable
internal fun VideoSurface(
    player: EditorPlayer,
    modifier: Modifier = Modifier,
) {
    key(player.generation) {
        AndroidView(
            modifier = modifier,
            factory = ::TextureView,
            // Runs right after factory too.
            update = { view ->
                player.setVideoTextureView(view)
            },
            onReset = { view ->
                player.clearVideoTextureView(view)
                view.surfaceTextureListener = null
            },
            onRelease = { view ->
                player.clearVideoTextureView(view)
                view.surfaceTextureListener = null
            },
        )
    }
}
