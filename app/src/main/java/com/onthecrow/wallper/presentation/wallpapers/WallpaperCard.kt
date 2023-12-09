package com.onthecrow.wallper.presentation.wallpapers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.onthecrow.wallper.presentation.wallpapers.models.Wallpaper

@Composable
fun WallpaperCard(
    wallpaper: Wallpaper,
    onCardClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(true, onClick = onCardClick)
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