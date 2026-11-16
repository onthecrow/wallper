package com.onthecrow.wallper.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.onthecrow.wallper.core.designsystem.R

/**
 * A round floating action button in the accent colour. While not [enabled] it is greyed out, as Material's
 * disabled buttons are, and a tap does nothing; the colours change in a moment.
 */
@Composable
fun FAB(
    painter: Painter = painterResource(id = R.drawable.ic_plus),
    contentDescription: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (enabled) colors.primary else colors.surfaceContainerHighest,
        label = "fab container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) colors.onPrimary else colors.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA),
        label = "fab content",
    )
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        modifier = Modifier.semantics { if (!enabled) disabled() },
    ) {
        Icon(painter = painter, contentDescription = contentDescription)
    }
}

/** Material's disabled content. */
private const val DISABLED_CONTENT_ALPHA = 0.38f
