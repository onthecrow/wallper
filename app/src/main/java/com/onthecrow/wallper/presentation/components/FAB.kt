package com.onthecrow.wallper.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.onthecrow.wallper.R

@Composable
fun FAB(onClick: () -> Unit) {
    FloatingActionButton(
        modifier = Modifier.wrapContentSize(),
        onClick = onClick
    ) {
        Image(
            modifier = Modifier.wrapContentSize(),
            contentScale = ContentScale.Fit,
            painter = painterResource(id = R.drawable.ic_plus),
            contentDescription = ""
        )
    }
}