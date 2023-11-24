package com.onthecrow.wallper.presentation.wallpaperlist

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.onthecrow.wallper.data.WallpaperEntity

@Composable
fun WallpaperCard(
    wallpaperEntity: WallpaperEntity,
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
            if (wallpaperEntity.bitmap != null) {
                Image(
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentScale = ContentScale.Crop,
                    painter = BitmapPainter(wallpaperEntity.bitmap!!),
                    contentDescription = ""
                )
            }
            if (wallpaperEntity.isActive) {
                Card(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .align(alignment = Alignment.BottomEnd),
                    colors = CardDefaults.cardColors(containerColor = Color.Green)
                ) {}
            }
        }
    }
}