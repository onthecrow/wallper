package com.onthecrow.wallper.presentation.wallpapers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.onthecrow.wallper.R
import com.onthecrow.wallper.presentation.wallpapers.models.Wallpaper

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    modifier: Modifier = Modifier,
    onCardClick: () -> Unit = {},
    onCardLongClick: () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    Card(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onCardClick()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCardLongClick()
                    },
                    onLongClickLabel = stringResource(R.string.open_context_menu)
                )
        ) {
            AsyncImage(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                model = wallpaper.picturePath,
                contentScale = ContentScale.Crop,
                contentDescription = null,
            )
            if (wallpaper.isActive) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(12.dp),
                    onDraw = {
                        drawCircle(Color.Green)
                    }
                )
            }
        }
    }
}

@Preview(
    widthDp = 200,
    heightDp = 200,
)
@Composable
fun WallpaperCardPreview() {
    WallpaperCard(
        wallpaper = Wallpaper(0, "", true),
    ) {}
}