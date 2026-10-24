package com.onthecrow.wallper.core.designsystem.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.onthecrow.wallper.core.designsystem.R

/** A round floating action button in the accent colour. */
@Composable
fun FAB(
    painter: Painter = painterResource(id = R.drawable.ic_plus),
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(painter = painter, contentDescription = contentDescription)
    }
}
