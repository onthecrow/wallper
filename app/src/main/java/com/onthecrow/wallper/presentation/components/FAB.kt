package com.onthecrow.wallper.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.onthecrow.wallper.R

@Composable
fun FAB(
    painter: Painter = painterResource(id = R.drawable.ic_plus),
    onClick: () -> Unit
) {
    FloatingActionButton(
        modifier = Modifier.wrapContentSize(),
        onClick = onClick
    ) {
        Image(
            painter = painter,
            contentDescription = ""
        )
    }
}