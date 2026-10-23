package com.onthecrow.wallper.blackhole.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.blackhole.physics.QualityLevel
import com.onthecrow.wallper.blackhole.ui.R

/** FPS, GPU time, the quality level and any loading or shader problem, over a live preview. */
@Composable
internal fun PreviewStats(
    fps: Double?,
    gpuMs: Double?,
    quality: QualityLevel,
    /** The level's scale capped by the render resolution setting. */
    renderScale: Float,
    isTablesLoading: Boolean,
    tablesError: String?,
    renderFailure: String?,
    modifier: Modifier = Modifier,
) {
    val style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
    Column(
        modifier = modifier
            .background(OverlayBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        val fpsText = fps?.let { stringResource(R.string.black_hole_debug_fps, it) }
            ?: stringResource(R.string.black_hole_debug_fps_unknown)
        val gpuText = gpuMs?.let { stringResource(R.string.black_hole_debug_gpu, it) }
        Text(text = listOfNotNull(fpsText, gpuText).joinToString(" · "), style = style, color = Color.White)
        Text(
            text = stringResource(R.string.black_hole_debug_level, quality.label(), renderScale),
            style = style,
            color = Color.White,
        )
        if (isTablesLoading) {
            Text(text = stringResource(R.string.black_hole_debug_tables_loading), style = style, color = Color.White)
        }
        tablesError?.let {
            Text(text = stringResource(R.string.black_hole_debug_tables_failed, it), style = style, color = ErrorText)
        }
        renderFailure?.let {
            Text(
                text = stringResource(R.string.black_hole_debug_shader_failed, it),
                style = style,
                color = ErrorText,
                maxLines = FAILURE_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun QualityLevel.label(): String = stringResource(
    when (this) {
        QualityLevel.LOW -> R.string.black_hole_quality_low
        QualityLevel.MEDIUM -> R.string.black_hole_quality_medium
        QualityLevel.HIGH -> R.string.black_hole_quality_high
    },
)

private val OverlayBackground = Color.Black.copy(alpha = 0.55f)
private val ErrorText = Color(0xFFFF8A80)
private const val FAILURE_MAX_LINES = 12
