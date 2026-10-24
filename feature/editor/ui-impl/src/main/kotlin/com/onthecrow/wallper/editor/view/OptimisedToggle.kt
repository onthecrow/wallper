package com.onthecrow.wallper.editor.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.editor.ui.R

/**
 * Original / optimised, like Material's expressive toggle button, which this Material version lacks: a
 * tonal pill when off, a primary rounded rectangle when on. The question mark on its right explains both
 * instead of toggling.
 */
@Composable
internal fun OptimisedToggle(
    isOptimised: Boolean,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue = if (isOptimised) colors.primary else colors.secondaryContainer,
        label = "optimised toggle container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isOptimised) colors.onPrimary else colors.onSecondaryContainer,
        label = "optimised toggle content",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isOptimised) CheckedCornerRadius else ButtonDefaults.MinHeight / 2,
        label = "optimised toggle corners",
    )
    Surface(
        checked = isOptimised,
        onCheckedChange = { onToggle() },
        modifier = modifier.height(ButtonDefaults.MinHeight),
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = isOptimised,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "optimised toggle label",
            ) { optimised ->
                Text(
                    text = stringResource(if (optimised) R.string.editor_optimised else R.string.editor_original),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onInfoClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_question_mark),
                    contentDescription = stringResource(R.string.editor_processing_info),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private val CheckedCornerRadius = 12.dp

@Preview
@Composable
private fun OptimisedTogglePreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OptimisedToggle(isOptimised = false, onToggle = {}, onInfoClick = {})
        OptimisedToggle(isOptimised = true, onToggle = {}, onInfoClick = {})
    }
}
